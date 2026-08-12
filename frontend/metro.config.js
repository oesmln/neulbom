// Metro needs to treat 3D models as static assets, otherwise it tries to parse
// the binary .glb as JavaScript source.
const path = require("path");
const { getDefaultConfig } = require("expo/metro-config");

const config = getDefaultConfig(__dirname);

for (const ext of ["glb", "gltf", "bin"]) {
  if (!config.resolver.assetExts.includes(ext)) config.resolver.assetExts.push(ext);
}

// three ships an ESM and a CJS build behind its "exports" map. Metro hands our
// `import` the ESM one and react-three-fiber's `require` the CJS one, so the
// library ends up loaded twice ("Multiple instances of Three.js"). That breaks
// the native loaders: react-three-fiber patches FileLoader/TextureLoader on its
// copy, while GLTFLoader keeps using the unpatched one and chokes on Metro's
// numeric asset ids. Pin every importer to the same build.
const THREE_ENTRY = path.resolve(__dirname, "node_modules/three/build/three.module.js");
const defaultResolveRequest = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === "three") return { type: "sourceFile", filePath: THREE_ENTRY };
  return (defaultResolveRequest ?? context.resolveRequest)(context, moduleName, platform);
};

module.exports = config;
