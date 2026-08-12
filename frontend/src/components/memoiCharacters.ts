/**
 * Memoi 3D character models.
 *
 * These point at `nono_dementia_glb/optimized/`, not at the Meshy exports
 * beside them. The raw exports are 187MB of 130k-701k triangle meshes wearing
 * four 2048x2048 maps each — far past what a phone will draw at the ~220px
 * this character occupies, and enough to take the app down once a few are
 * resident. The optimized set is decimated to 25k triangles, resized to 512px,
 * and stripped of the two maps the renderer never binds: ~1MB each, identical
 * skeleton and "idle" clip.
 *
 * The originals are kept as the source of truth. Metro only bundles assets
 * something requires, so leaving them in place costs nothing at build time.
 * To regenerate after adding a character, see the note in CLAUDE.md.
 */

export type MemoiCharacter = {
  id: string;
  /** Korean label shown next to the character. */
  name: string;
  /** Metro asset module — handed straight to useLoader(GLTFLoader, ...). */
  module: number;
};

export const MEMOI_CHARACTERS: MemoiCharacter[] = [
  // The plain red bean, no accessories. This is the character the app ships
  // with; the rest are growth stages waiting on `GET /character/{user_id}` to
  // say which one a user has reached.
  { id: "memoi-1", name: "메모이", module: require("../../assets/models/optimized/1_final.glb") },
  { id: "memoi-2", name: "메모이 2", module: require("../../assets/models/optimized/2_final.glb") },
  { id: "memoi-3", name: "메모이 3", module: require("../../assets/models/optimized/3_final.glb") },
  { id: "memoi-4", name: "메모이 4", module: require("../../assets/models/optimized/4_final.glb") },
  { id: "memoi-5", name: "메모이 5", module: require("../../assets/models/optimized/5_final.glb") },
  { id: "memoi-6", name: "메모이 6", module: require("../../assets/models/optimized/6_final.glb") },
];

export const DEFAULT_MEMOI = MEMOI_CHARACTERS[0];

/* ----------------------------------------------------------- mouth shapes */

/**
 * Mouth shapes for the talking character.
 *
 * Each set is one character sculpted three times, holding a different Korean
 * vowel. Swapping the whole model per phoneme is the crude way to do this —
 * proper lip sync wants morph targets on a single mesh — but Meshy exports
 * separate meshes, so a set is what we have. api-spec 12.6 puts TTS and
 * viseme playback in Phase 2; these are staged here so that work has something
 * to consume, and nothing drives them yet.
 *
 * Note the memory rule from `Memoi3D`: only one model is resident at a time,
 * and switching disposes the previous one. Cycling these at speech rate would
 * thrash the GPU — a real implementation needs all three shapes of one set
 * held together, which is a change to `Memoi3D`, not to this table.
 */
export type MemoiVowel = "eu" | "o" | "e";

export const MEMOI_VOWEL_LABELS: Record<MemoiVowel, string> = {
  eu: "으",
  o: "오",
  e: "에",
};

export type MemoiMouthSet = {
  id: string;
  name: string;
  shapes: Record<MemoiVowel, MemoiCharacter>;
};

export const MEMOI_MOUTH_SETS: MemoiMouthSet[] = [
  {
    id: "bean",
    name: "메모이 (기본)",
    shapes: {
      eu: {
        id: "bean-eu",
        name: "메모이 · 으",
        module: require("../../assets/models/optimized/Meshy_AI_Crimson_Bean_0811174208_texture.glb"),
      },
      o: {
        id: "bean-o",
        name: "메모이 · 오",
        module: require("../../assets/models/optimized/Meshy_AI_Joyful_Bean_0811174216_texture.glb"),
      },
      e: {
        id: "bean-e",
        name: "메모이 · 에",
        module: require("../../assets/models/optimized/Meshy_AI_Crimson_Bean_0811174225_texture.glb"),
      },
    },
  },
  {
    id: "straw-hat",
    name: "메모이 (밀짚모자)",
    shapes: {
      eu: {
        id: "straw-hat-eu",
        name: "밀짚모자 · 으",
        module: require("../../assets/models/optimized/Meshy_AI_Red_Bean_in_a_Straw_H_0811174426_texture.glb"),
      },
      // Not in filename order: 0441 is the small rounded 오 and 0434 is the
      // wide-open 에, the reverse of how the bean set came out of Meshy. Both
      // were checked on device before this table was written.
      o: {
        id: "straw-hat-o",
        name: "밀짚모자 · 오",
        module: require("../../assets/models/optimized/Meshy_AI_Pepper_with_a_Straw_H_0811174441_texture.glb"),
      },
      e: {
        id: "straw-hat-e",
        name: "밀짚모자 · 에",
        module: require("../../assets/models/optimized/Meshy_AI_Red_Bean_with_a_Straw_0811174434_texture.glb"),
      },
    },
  },
];

/** The set that matches `DEFAULT_MEMOI` — same bean, three mouths. */
export const DEFAULT_MOUTH_SET = MEMOI_MOUTH_SETS[0];

/** Every mouth shape, flattened — set order, then 으 → 오 → 에. */
export const MEMOI_MOUTH_SHAPES: MemoiCharacter[] = MEMOI_MOUTH_SETS.flatMap((set) => [
  set.shapes.eu,
  set.shapes.o,
  set.shapes.e,
]);
