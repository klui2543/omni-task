// Tells the settings file sync (settingsSync.ts) that a choice it carries changed on this device. Kept apart, with no
// imports, so every store can call it without import loops. Quick changes in a row become one write.

let listener: (() => void) | null = null
let timer: ReturnType<typeof setTimeout> | null = null

export const settingsBus = {
  /** A choice that travels between devices changed here; it is written a moment later, as Android does. */
  changed() {
    if (!listener) return
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      listener?.()
    }, 1500)
  },
  /** Who writes the file; returns a function that stops listening. */
  listen(fn: () => void): () => void {
    listener = fn
    return () => {
      if (listener === fn) listener = null
    }
  },
}
