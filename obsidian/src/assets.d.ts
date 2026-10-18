// The WebAssembly the build carries inside main.js as bytes (esbuild's binary loader).
declare module '*.wasm' {
  const bytes: Uint8Array
  export default bytes
}
