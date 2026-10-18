// The app's styles as text, for the tab inside Obsidian: the plugin's build turns each `import './x.css'` into a call
// to [addCss] with the file already scoped to the tab (obsidian/esbuild.config.mjs). The web page never uses this.
export const css: string[] = []

export function addCss(text: string) {
  css.push(text)
}
