/**
 * Memoi — the MemoCare mascot, rendered as a real 3D character.
 *
 * Runs three.js on top of expo-gl. The GLB models are rigged and carry a single
 * looping "idle" clip, which is what makes the character feel alive on screen.
 * Falls back to the emoji avatar if the GL context or the model fails to load,
 * so a device without working GL never shows an empty card.
 *
 * Memory is the constraint here, not frame time. Each Meshy export uploads two
 * 2048x2048 maps (~42MB of GPU memory) and useLoader caches what it parses for
 * the lifetime of the app, so cycling through the six characters would pile up
 * a quarter of a gigabyte and take the app down with it. Exactly one character
 * is kept resident: switching disposes the previous one and drops its cache
 * entry, trading a reload on the way back for an app that survives.
 */
import React, { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, StyleSheet, View, ViewStyle, StyleProp } from "react-native";
import { Canvas, useFrame, useLoader } from "@react-three/fiber/native";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import { clone as cloneSkinned } from "three/examples/jsm/utils/SkeletonUtils.js";
import * as THREE from "three";

import { colors } from "@/theme";
import { Avatar } from "./ui";
import { MemoiCharacter, MemoiMouthSet } from "./memoiCharacters";

/** Every character is rescaled to this height so they all frame identically. */
const TARGET_HEIGHT = 2;
/** Share of the canvas height the character fills once framed. */
const FRAME_FILL = 0.82;
/** Vertical field of view, in degrees. Must match the camera below. */
const FOV = 40;
/** Pulls the camera back just far enough to satisfy FRAME_FILL. */
const CAMERA_DISTANCE =
  TARGET_HEIGHT / FRAME_FILL / 2 / Math.tan((FOV / 2) * (Math.PI / 180));
/**
 * Poses sampled across the idle loop when measuring the character. One sample
 * is not enough: the clip moves the body, so framing a single instant leaves
 * the character riding high or low for the rest of the loop.
 */
const FRAMING_SAMPLES = 6;
/**
 * Only every Nth vertex is measured. On a mesh this dense the bounds land
 * within a fraction of a percent of the full sweep, and visiting all 25k
 * vertices once per pose costs over a second on a mid-range phone.
 */
const FRAMING_VERTEX_STRIDE = 8;
/**
 * How long each mouth shape is held while the character talks.
 *
 * Speech is roughly 5-7 syllables a second, so this is deliberately slower than
 * real speech — it reads as talking without turning into a strobe. Once TTS
 * lands the cadence should come from the audio rather than from a timer.
 */
const MOUTH_SHAPE_MS = 170;

/* ------------------------------------------------------------------ framing */

/**
 * Grows `box` to cover the character in its current pose.
 *
 * `Box3.setFromObject(root, true)` does the same thing, but only the `precise`
 * form asks the skinned mesh where its vertices actually are — the default
 * reads a bounding box cached from the bind pose and ignores the skeleton
 * entirely. Precise visits every vertex though, so this walks them with a
 * stride instead.
 */
function expandToPose(root: THREE.Object3D, box: THREE.Box3, scratch: THREE.Vector3) {
  root.traverse((object) => {
    const mesh = object as THREE.Mesh;
    if (!mesh.isMesh) return;

    const position = mesh.geometry?.getAttribute("position");
    if (!position) return;

    for (let i = 0; i < position.count; i += FRAMING_VERTEX_STRIDE) {
      mesh.getVertexPosition(i, scratch);
      box.expandByPoint(scratch.applyMatrix4(mesh.matrixWorld));
    }
  });
}

/* ----------------------------------------------------------------- disposal */

/** Releases every GPU resource reachable from a loaded scene. */
function disposeScene(root: THREE.Object3D) {
  root.traverse((object) => {
    const mesh = object as THREE.Mesh;
    if (!mesh.isMesh) return;

    mesh.geometry?.dispose();

    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    for (const material of materials) {
      if (!material) continue;
      // Texture slots vary by material type, so sweep every property rather
      // than naming them.
      for (const value of Object.values(material)) {
        const texture = value as THREE.Texture | null;
        if (texture && texture.isTexture) texture.dispose();
      }
      material.dispose();
    }
  });
}

/* ------------------------------------------------------------------- model */

function MemoiModel({
  character,
  spin,
  visible,
  onReady,
}: {
  character: MemoiCharacter;
  spin: boolean;
  /**
   * Hidden models stay loaded and stay put. Mouth shapes are swapped at speech
   * rate, which is far too fast to mount and dispose a GLB between frames.
   */
  visible: boolean;
  onReady: (framingMs: number) => void;
}) {
  // Metro hands us a numeric asset id; react-three-fiber's native FileLoader
  // polyfill resolves it through expo-asset.
  const source = character.module as unknown as string;
  const gltf = useLoader(GLTFLoader, source);
  const group = useRef<THREE.Group>(null);

  const { scene, mixer, fit, framingMs } = useMemo(() => {
    const startedAt = Date.now();
    // useLoader caches by asset, so clone before touching anything — a skinned
    // mesh cannot be shared between two mounts without the skeletons fighting.
    const root = cloneSkinned(gltf.scene);

    root.traverse((object) => {
      const mesh = object as THREE.Mesh;
      if (!mesh.isMesh) return;

      // Skinned bounds are computed from the bind pose and cull incorrectly
      // once the idle clip moves the character.
      mesh.frustumCulled = false;

      const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
      for (const material of materials) {
        const std = material as THREE.MeshStandardMaterial;
        if (!std.isMeshStandardMaterial) continue;

        // Meshy exports metallicFactor = 1. Fully metallic surfaces take their
        // colour from an environment map, and with none bound the character
        // renders black — this is the fix that makes the baked colours show.
        std.metalness = 0;
        std.roughness = 0.85;

        // Roughness/metalness now come from the scalars above, and the emissive
        // map is a flat black 2048² JPEG. Dropping all three keeps ~64MB of
        // texture memory off the GPU without changing how the character looks;
        // dispose them rather than just unhooking so the handles go too.
        std.metalnessMap?.dispose();
        std.roughnessMap?.dispose();
        std.emissiveMap?.dispose();
        std.metalnessMap = null;
        std.roughnessMap = null;
        std.emissiveMap = null;
        std.emissive = new THREE.Color(0x000000);

        std.needsUpdate = true;
      }
    });

    // The idle clip has to be running before the character can be measured,
    // so the mixer is built here rather than in a second pass.
    const animator = new THREE.AnimationMixer(root);
    const idle = gltf.animations.find((clip) => clip.name === "idle") ?? gltf.animations[0];
    if (idle) animator.clipAction(idle).play();

    // Measure the character across the whole loop and frame the union, so it
    // stays centred for every pose rather than for one instant. Characters
    // whose rig carries them off the origin — 3 sits a unit high, 4 a unit
    // low — were hugging the top of the card before this.
    const bounds = new THREE.Box3();
    const scratch = new THREE.Vector3();
    const sampleCount = idle ? FRAMING_SAMPLES : 1;
    for (let i = 0; i < sampleCount; i++) {
      if (idle) animator.setTime((idle.duration * i) / sampleCount);
      root.updateMatrixWorld(true);
      expandToPose(root, bounds, scratch);
    }
    animator.setTime(0);

    const size = bounds.getSize(new THREE.Vector3());
    const centre = bounds.getCenter(new THREE.Vector3());
    const scale = TARGET_HEIGHT / (size.y || 1);

    // Applied to a wrapper group rather than to `root`: the clip may animate
    // the root node itself, and the mixer would overwrite anything set here.
    const framing = {
      scale,
      offset: new THREE.Vector3(-centre.x * scale, -centre.y * scale, -centre.z * scale),
    };

    return { scene: root, mixer: animator, fit: framing, framingMs: Date.now() - startedAt };
  }, [gltf]);

  // Held in a ref so an inline callback from the parent cannot retrigger the
  // effect below — re-running it would tear the live character down mid-frame.
  const readyRef = useRef(onReady);
  readyRef.current = onReady;

  useEffect(() => {
    readyRef.current(framingMs);

    return () => {
      mixer.stopAllAction();
      mixer.uncacheRoot(scene);

      // SkeletonUtils.clone shares geometry and materials with the cached
      // original, so disposing the clone frees both. The cache entry has to go
      // in the same breath: leave it and the next mount hands back a scene
      // whose GPU resources have already been released.
      disposeScene(scene);
      useLoader.clear(GLTFLoader, source);
    };
  }, [mixer, scene, source, framingMs]);

  useFrame((_, delta) => {
    // Skinning an off-screen rig costs the same as an on-screen one, and a
    // talking character has three of them parked behind the visible mouth.
    if (!visible) return;
    mixer.update(delta);
    if (spin && group.current) group.current.rotation.y += delta * 0.35;
  });

  return (
    <group ref={group} visible={visible}>
      <group scale={fit.scale} position={fit.offset}>
        <primitive object={scene} />
      </group>
    </group>
  );
}

/* ----------------------------------------------------------- error boundary */

class GLBoundary extends React.Component<
  { children: React.ReactNode; fallback: React.ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

/* -------------------------------------------------------------------- view */

/** Mouth shapes do not gate the spinner — only the resting face does. */
function noop() {}

export default function Memoi3D({
  character,
  mouthSet,
  speaking = false,
  height = 220,
  spin = false,
  spinnerColor = colors.white,
  style,
}: {
  character: MemoiCharacter;
  /**
   * Mouth shapes to keep loaded alongside `character`, enabling `speaking`.
   *
   * Supplying this costs three extra models resident (~3MB of GPU memory each)
   * for as long as the component is mounted, so pass it only on screens where
   * the character actually talks.
   */
  mouthSet?: MemoiMouthSet;
  /** Cycles 으 → 오 → 에 while true. Falls back to `character` when false. */
  speaking?: boolean;
  height?: number;
  /** Slowly turns the character on the spot. Off by default — it faces front. */
  spin?: boolean;
  /** Loading indicator tint. Defaults to white for the sage and dark stages. */
  spinnerColor?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const [ready, setReady] = useState(false);

  const shapes = useMemo(
    () => (mouthSet ? [mouthSet.shapes.eu, mouthSet.shapes.o, mouthSet.shapes.e] : []),
    [mouthSet],
  );

  // -1 is the resting face. The interval is the placeholder for TTS timing.
  const [shapeIndex, setShapeIndex] = useState(-1);
  useEffect(() => {
    if (!speaking || shapes.length === 0) {
      setShapeIndex(-1);
      return;
    }
    setShapeIndex(0);
    const timer = setInterval(
      () => setShapeIndex((i) => (i + 1) % shapes.length),
      MOUTH_SHAPE_MS,
    );
    return () => clearInterval(timer);
  }, [speaking, shapes]);

  const activeId = shapeIndex >= 0 && shapes[shapeIndex] ? shapes[shapeIndex].id : character.id;

  // Stamped when the character changes and read again once the model is on
  // screen, so switching costs show up in the dev log per GLB.
  const startedAt = useRef(Date.now());

  const handleReady = useCallback(
    (framingMs: number) => {
      setReady(true);
      if (__DEV__) {
        const total = Date.now() - startedAt.current;
        console.log(`[Memoi3D] ${character.id} ready in ${total}ms (framing ${framingMs}ms)`);
      }
    },
    [character.id],
  );

  useEffect(() => {
    startedAt.current = Date.now();
    setReady(false);
  }, [character.id]);

  const fallback = (
    <View style={styles.centre}>
      <Avatar size={Math.min(height * 0.6, 96)} />
    </View>
  );

  return (
    <View
      style={[{ height }, style]}
      // The canvas installs its own touch handlers and would swallow every tap
      // before it reaches whatever wraps the character. Memoi is decorative, so
      // let touches fall through to the parent.
      pointerEvents="none"
      accessibilityRole="image"
      accessibilityLabel={`${character.name} 캐릭터`}
    >
      <GLBoundary fallback={fallback}>
        {/* One canvas for every character. Keying it per character would tear
            down and rebuild the whole GL context on each switch, which expo-gl
            is slow at and does not fully reclaim — the key belongs on the model
            inside instead. */}
        <Canvas
          style={StyleSheet.absoluteFill}
          // Dead-on and pulled back exactly far enough to satisfy FRAME_FILL,
          // so the centred character lands in the middle of the card.
          camera={{ position: [0, 0, CAMERA_DISTANCE], fov: FOV }}
          // expo-gl always renders at native device resolution (r3f pins dpr to
          // PixelRatio.get()), so MSAA on top of a 3x buffer buys very little
          // for a card this size and costs real fill rate on mid-range phones.
          gl={{ antialias: false }}
          onCreated={(state) => {
            // The Meshy textures are already baked to their final look; tone
            // mapping would wash the colours out.
            state.gl.toneMapping = THREE.NoToneMapping;
          }}
        >
          <ambientLight intensity={1.1} />
          <directionalLight position={[3, 5, 4]} intensity={1.5} />
          <directionalLight position={[-4, 2, -3]} intensity={0.45} />
          {/* One Suspense boundary each, so the resting face shows as soon as
              it parses instead of waiting on the three mouth shapes behind it. */}
          <Suspense fallback={null}>
            <MemoiModel
              key={character.id}
              character={character}
              spin={spin}
              visible={activeId === character.id}
              onReady={handleReady}
            />
          </Suspense>
          {shapes.map((shape) => (
            <Suspense key={shape.id} fallback={null}>
              <MemoiModel
                character={shape}
                spin={spin}
                visible={activeId === shape.id}
                onReady={noop}
              />
            </Suspense>
          ))}
        </Canvas>
      </GLBoundary>

      {ready ? null : (
        <View style={[StyleSheet.absoluteFill, styles.centre]} pointerEvents="none">
          <ActivityIndicator size="large" color={spinnerColor} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  centre: { flex: 1, alignItems: "center", justifyContent: "center" },
});
