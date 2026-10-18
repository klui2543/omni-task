import { describe, expect, it } from 'vitest'
import { scopeCss } from '../scripts/scopeCss.mjs'

describe('the app\'s styles in a tab', () => {
  it('puts the page\'s root and body on the tab\'s root', () => {
    expect(scopeCss(':root { --bg: #fff; }')).toBe('.omni-root { --bg: #fff; }')
    expect(scopeCss('html, body { margin: 0; }')).toBe('.omni-root { margin: 0; }')
    expect(scopeCss('body.dragging, body.dragging * { cursor: grabbing; }')).toBe('.omni-root.dragging, .omni-root.dragging * { cursor: grabbing; }')
    expect(scopeCss(':root[data-theme=dark] .dock { color: #000; }')).toBe('.omni-root[data-theme=dark] .dock { color: #000; }')
    expect(scopeCss('body:has(.page) .dock { display: none; }')).toBe('.omni-root:has(.page) .dock { display: none; }')
    // A class that only ends in "body" is left alone.
    expect(scopeCss('.as-body { gap: 0; } tbody { x: 1; }')).toBe('.as-body { gap: 0; } tbody { x: 1; }')
  })

  it('asks the tab\'s width and height instead of the window\'s', () => {
    expect(scopeCss('@media (min-width: 1000px) { .side { display: flex; } }')).toBe('@container omni (min-width: 1000px) { .side { display: flex; } }')
    expect(scopeCss('@media (prefers-reduced-motion: reduce) { a { b: c } }')).toBe('@media (prefers-reduced-motion: reduce) { a { b: c } }')
    expect(scopeCss('.a { height: 100vh; height: 100dvh; max-height: calc(100dvh - 80px); }')).toBe('.a { height: 100cqh; height: 100cqh; max-height: calc(100cqh - 80px); }')
  })

  it('leaves the safe areas to Obsidian', () => {
    expect(scopeCss('.dock { bottom: max(14px, env(safe-area-inset-bottom)); }')).toBe('.dock { bottom: max(14px, 0px); }')
  })
})
