// WebP for the images the app attaches, where the WebView cannot write it from a canvas (Obsidian on the iPad).
// libwebp compiled to WebAssembly, carried inside the plugin (a plugin is one file), started the first time it is needed.
import factory from '@jsquash/webp/codec/enc/webp_enc.js'
import { defaultOptions } from '@jsquash/webp/meta.js'
import { initEmscriptenModule } from '@jsquash/webp/utils.js'
import wasm from '@jsquash/webp/codec/enc/webp_enc.wasm'

interface Encoder {
  encode(data: Uint8ClampedArray, width: number, height: number, options: typeof defaultOptions): Uint8Array | null
}

let encoder: Promise<Encoder> | null = null

export async function webp(image: ImageData, quality: number): Promise<ArrayBuffer> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  encoder ??= WebAssembly.compile(wasm).then((m) => initEmscriptenModule(factory as any, m) as unknown as Promise<Encoder>)
  const out = (await encoder).encode(image.data, image.width, image.height, { ...defaultOptions, quality })
  if (!out) throw new Error('แปลงรูปเป็น WebP ไม่ได้')
  return out.buffer as ArrayBuffer
}
