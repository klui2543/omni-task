// The web app's styles, made to live in a tab of Obsidian instead of a page of their own (see web/src/embed.tsx):
// - the page's root and body are the tab's root element (.omni-root), so the colours and fonts stay inside the tab;
// - a width rule asks the tab's width, not the window's (a container query on the tab's box, named omni);
// - a height of the screen is the tab's height;
// - the phone's safe areas are Obsidian's to keep, so the app adds none of its own.

const ROOT = '.omni-root'

/** Turns one CSS file of the web app into its scoped form for the tab. */
export function scopeCss(css) {
  return css
    // :root, and the page itself (html, body), become the tab's root element.
    .replace(/:root\b/g, ROOT)
    .replace(/(^|[\s,{}(>+~])(?:html|body)(?=[\s.,:{[#>+~)])/gm, `$1${ROOT}`)
    // "html, body" turned into ".omni-root, .omni-root": once is enough.
    .replace(new RegExp(`${escape(ROOT)}\\s*,\\s*${escape(ROOT)}(?=[\\s{,])`, 'g'), ROOT)
    // Width-only media queries ask the tab's box.
    .replace(/@media\s+((?:\((?:min|max)-width:\s*[\d.]+px\)\s*(?:and\s*)?)+)\{/g, (_, q) => `@container omni ${q.trim()} {`)
    // Screen heights and widths are the tab's.
    .replace(/(\d*\.?\d+)(?:d|s|l)?vh\b/g, '$1cqh')
    .replace(/(\d*\.?\d+)(?:d|s|l)?vw\b/g, '$1cqw')
    .replace(/env\(\s*safe-area-inset-[a-z]+\s*(?:,[^()]*(?:\([^()]*\))?[^()]*)?\)/g, '0px')
}

function escape(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
