/**
 * Shrinks the raw Meshy character exports into the set the app actually ships.
 *
 * Not part of the build — run it by hand after adding or replacing a model in
 * `assets/models/`. It never writes over its input.
 *
 *   npm i --no-save @gltf-transform/core @gltf-transform/functions \
 *                   @gltf-transform/extensions meshoptimizer sharp
 *   node optimize-glb.mjs
 *
 * Meshy exports are built for offline rendering: 170k-571k triangles and four
 * 2048x2048 maps per character, 88MB across the six of them. Memoi is drawn
 * ~220px tall, and Memoi3D.tsx overrides metalness/roughness with scalars and
 * blacks out emissive, so two of those four maps are never even bound. This
 * decimates to 25k triangles, resizes to 512px, drops the unused maps, and
 * leaves the skeleton and the "idle" clip untouched — 6MB in total, and ~3MB
 * of GPU memory per character instead of ~42MB.
 */
import fs from "node:fs";
import path from "node:path";

import { NodeIO } from "@gltf-transform/core";
import { ALL_EXTENSIONS } from "@gltf-transform/extensions";
import { dedup, prune, weld, simplify, textureCompress } from "@gltf-transform/functions";
import { MeshoptSimplifier } from "meshoptimizer";
import sharp from "sharp";

const SRC = process.argv[2] ?? "assets/models";
const OUT = process.argv[3] ?? "assets/models/optimized";

/** Plenty of silhouette at the size Memoi is rendered. */
const TARGET_TRIS = 25000;
/** 512 is already generous for a 220px character; 2048 was 16x more than needed. */
const TEXTURE_SIZE = 512;

await MeshoptSimplifier.ready;
fs.mkdirSync(OUT, { recursive: true });

const io = new NodeIO().registerExtensions(ALL_EXTENSIONS);

function countTris(doc) {
  let tris = 0;
  for (const mesh of doc.getRoot().listMeshes()) {
    for (const prim of mesh.listPrimitives()) {
      const indices = prim.getIndices();
      const count = indices ? indices.getCount() : prim.getAttribute("POSITION").getCount();
      tris += count / 3;
    }
  }
  return Math.round(tris);
}

let sumBefore = 0;
let sumAfter = 0;

for (const file of fs.readdirSync(SRC).filter((f) => f.endsWith(".glb")).sort()) {
  const srcPath = path.join(SRC, file);
  const doc = await io.read(srcPath);
  const beforeBytes = fs.statSync(srcPath).size;
  const beforeTris = countTris(doc);

  // Unhook the two maps the renderer ignores and bake in the scalar values it
  // sets at runtime, so prune() can delete the images outright.
  for (const material of doc.getRoot().listMaterials()) {
    material.setMetallicRoughnessTexture(null);
    material.setEmissiveTexture(null);
    material.setMetallicFactor(0);
    material.setRoughnessFactor(0.85);
  }

  await doc.transform(
    dedup(),
    weld(),
    simplify({ simplifier: MeshoptSimplifier, ratio: Math.min(1, TARGET_TRIS / beforeTris), error: 0.005 }),
    textureCompress({ encoder: sharp, targetFormat: "jpeg", resize: [TEXTURE_SIZE, TEXTURE_SIZE], quality: 88 }),
    prune({ keepAttributes: false }),
  );

  const outPath = path.join(OUT, file);
  await io.write(outPath, doc);

  const afterBytes = fs.statSync(outPath).size;
  sumBefore += beforeBytes;
  sumAfter += afterBytes;

  console.log(
    `${file}: ${(beforeBytes / 1048576).toFixed(1)}MB -> ${(afterBytes / 1048576).toFixed(2)}MB   ` +
      `${beforeTris} -> ${countTris(doc)} tris   textures ${doc.getRoot().listTextures().length}`,
  );
}

console.log(
  `\nTOTAL ${(sumBefore / 1048576).toFixed(1)}MB -> ${(sumAfter / 1048576).toFixed(2)}MB ` +
    `(${((sumAfter / sumBefore) * 100).toFixed(1)}% of original)`,
);
