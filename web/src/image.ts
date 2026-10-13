// A picked photo turned into WebP for the vault, as Android's ImageAttach does. Drawing it again from its pixels
// also drops EXIF, so GPS and camera data never reach the vault.

export const MAX_SIDE = 2048
export const MAX_BYTES = 1_000_000
const QUALITY = 0.8

/** Both encodings, so the owner can choose when the full-size one is big. */
export interface Prepared {
  width: number
  height: number
  full: Blob
  reduced: Blob | null
}

/** Ask only when shrinking would actually help. */
export const needsChoice = (p: Prepared) => p.reduced !== null && (p.full.size > MAX_BYTES || Math.max(p.width, p.height) > MAX_SIDE)

async function encode(source: CanvasImageSource, w: number, h: number): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const g = canvas.getContext('2d')!
  g.drawImage(source, 0, 0, w, h)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', QUALITY))
  if (blob && blob.type === 'image/webp') return blob
  // Safari (the iPad) cannot write WebP from a canvas and hands back a PNG. The same pixels go through libwebp
  // compiled to WebAssembly instead, fetched only the first time it is needed.
  const { default: toWebp } = await import('@jsquash/webp/encode.js')
  const bytes = await toWebp(g.getImageData(0, 0, w, h), { quality: QUALITY * 100 })
  return new Blob([bytes], { type: 'image/webp' })
}

export async function prepare(file: Blob): Promise<Prepared> {
  const bitmap = await createImageBitmap(file)
  const { width, height } = bitmap
  const full = await encode(bitmap, width, height)
  const long = Math.max(width, height)
  let reduced: Blob | null = null
  if (long > MAX_SIDE || full.size > MAX_BYTES) {
    const scale = Math.min(1, MAX_SIDE / long)
    reduced = await encode(bitmap, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)))
  }
  bitmap.close()
  return { width, height, full, reduced }
}

const two = (n: number) => String(n).padStart(2, '0')

/** Omni-YYYY-MM-DD-HHmmss.webp, as Android names them. */
export const fileName = (d = new Date()) =>
  `Omni-${d.getFullYear()}-${two(d.getMonth() + 1)}-${two(d.getDate())}-${two(d.getHours())}${two(d.getMinutes())}${two(d.getSeconds())}.webp`

export const sizeLabel = (bytes: number) => (bytes >= 1_000_000 ? `${(bytes / 1_000_000).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1000))} KB`)
