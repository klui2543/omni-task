"""Tiny pixel-art engine for the Omni Quest sprites: shapes become masks, parts get cel shading and a
dark outline automatically, flames are drawn procedurally. Run make_sprites.py to rebuild the PNGs."""
import numpy as np
from PIL import Image, ImageDraw

OUT = (0x1a, 0x14, 0x24)


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def dark(c, k=0.7):
    return (int(c[0] * k), int(c[1] * k * 0.93), min(255, int(c[2] * (k + 0.05))))


def light(c, k=0.3):
    return tuple(int(v + (255 - v) * k) for v in c)


def shift(m, dx, dy):
    r = np.zeros_like(m)
    H, W = m.shape
    ys, yd = (slice(max(0, dy), H + min(0, dy)), slice(max(0, -dy), H - max(0, dy)))
    xs, xd = (slice(max(0, dx), W + min(0, dx)), slice(max(0, -dx), W - max(0, dx)))
    r[ys, xs] = m[yd, xd]
    return r


def erode(m):
    return m & shift(m, 1, 0) & shift(m, -1, 0) & shift(m, 0, 1) & shift(m, 0, -1)


def dilate(m):
    return m | shift(m, 1, 0) | shift(m, -1, 0) | shift(m, 0, 1) | shift(m, 0, -1)


class Sprite:
    def __init__(self, W, H):
        self.W, self.H = W, H
        self.parts = []
        self.top = []      # drawn after the outline (flame on the forehead, sparkles)
        self.aura = []     # drawn behind everything with its own edge
        yy, xx = np.mgrid[0:H, 0:W]
        self.xx, self.yy = xx + .5, yy + .5

    # shapes -> masks
    def ell(self, cx, cy, rx, ry):
        return ((self.xx - cx) / rx) ** 2 + ((self.yy - cy) / ry) ** 2 <= 1

    def rect(self, x0, y0, x1, y1):
        return (self.xx >= x0) & (self.xx < x1) & (self.yy >= y0) & (self.yy < y1)

    def poly(self, pts):
        im = Image.new('L', (self.W, self.H), 0)
        ImageDraw.Draw(im).polygon([(x, y) for x, y in pts], fill=1)
        return np.array(im, dtype=bool)

    def line(self, pts, w=1):
        im = Image.new('L', (self.W, self.H), 0)
        ImageDraw.Draw(im).line([(x, y) for x, y in pts], fill=1, width=w)
        return np.array(im, dtype=bool)

    def ring(self, cx, cy, rx, ry, t=1.3):
        return self.ell(cx, cy, rx, ry) & ~self.ell(cx, cy, rx - t, ry - t)

    def px(self, pts):
        m = np.zeros((self.H, self.W), bool)
        for x, y in pts:
            if 0 <= x < self.W and 0 <= y < self.H:
                m[int(y), int(x)] = True
        return m

    # parts
    def add(self, m, color, shade=True, edge=False, rim=True):
        self.parts.append((m, rgb(color), shade, edge, rim))

    def flat(self, m, color):
        self.add(m, color, shade=False, rim=False)

    def flame(self, cx, base, h, w, color, where='aura', sway=1.5, phase=0.0, core=True):
        self.aura.append(('flame', cx, base, h, w, rgb(color), sway, phase, core)) if where == 'aura' else \
            self.top.append(('flame', cx, base, h, w, rgb(color), sway, phase, core))

    def spark(self, pts, color):
        self.top.append(('px', pts, rgb(color)))

    # drawing
    def _flame_mask(self, cx, base, h, w, sway, phase, k=1.0):
        """A teardrop flame: round belly, a tip that bends to one side, and a small tongue beside it."""
        m = np.zeros((self.H, self.W), bool)
        h, w = max(4, h * k), max(3, w * k)
        r0 = w / 2
        belly = ((self.xx - cx) / r0) ** 2 + ((self.yy - (base + 0.5 - r0)) / (r0 * 1.05)) ** 2 <= 1
        m |= belly
        side = 1 if sway >= 0 else -1
        sway = abs(sway)
        for tongue, (ox, th, tw) in enumerate([(0.0, 1.0, 1.0), (-side * w * 0.42, 0.52, 0.55)]):
            tip_h = (h - r0) * th
            for r in range(int(tip_h) + 1):
                t = r / max(1.0, tip_h)
                half = (r0 * tw) * (1 - t) ** 1.25
                c = cx + ox + side * sway * 2.2 * t ** 2 * (1 if tongue == 0 else 0.6)
                y = int(base - r0 - r)
                for x in range(int(round(c - half)), int(round(c + half)) + 1):
                    if 0 <= x < self.W and 0 <= y < self.H:
                        m[y, x] = True
        return m

    def _paint(self, img, items):
        """Flames of one colour are merged first so overlapping tongues share one edge, then toned: rim, body, light, core."""
        groups = {}
        for it in items:
            if it[0] == 'px':
                for x, y in it[1]:
                    if 0 <= x < self.W and 0 <= y < self.H:
                        img[int(y), int(x)] = it[2] + (255,)
                continue
            groups.setdefault(it[5], []).append(it)
        for col, its in groups.items():
            outer = np.zeros((self.H, self.W), bool); mid = outer.copy(); core = outer.copy()
            for _, cx, base, h, w, _c, sway, phase, c in its:
                outer |= self._flame_mask(cx, base, h, w, sway, phase)
                mid |= self._flame_mask(cx, base - 1, h, w, sway, phase, 0.66)
                if c:
                    core |= self._flame_mask(cx, base - 1, h, w, sway, phase, 0.36)
            edge = outer & ~erode(outer)
            img[outer] = col + (255,)
            img[mid] = light(col, 0.4) + (255,)
            img[core] = light(col, 0.85) + (255,)
            img[edge] = dark(col, 0.55) + (255,)

    def render(self):
        img = np.zeros((self.H, self.W, 4), np.uint8)
        self._paint(img, self.aura)
        ch = np.zeros_like(img)
        for m, c, shade, edge, rim in self.parts:
            ch[m] = c + (255,)
            if shade:
                sh = m & ~shift(m, -2, -1)
                ch[sh] = dark(c) + (255,)
                if rim:
                    hl = m & ~shift(m, 1, 1) & ~sh
                    ch[hl] = light(c, 0.35) + (255,)
            if edge:
                e = m & ~erode(m)
                ch[e] = dark(c, 0.55) + (255,)
        alpha = ch[..., 3] > 0
        outline = dilate(alpha) & ~alpha
        ch[outline] = OUT + (255,)
        full = alpha | outline
        img[full] = ch[full]
        self._paint(img, self.top)
        return Image.fromarray(img, 'RGBA')


def eye(s, x, y, iris='#3a2a4a', w=2, h=3, shine=True):
    """Anime eye: dark top lid, iris, one white shine pixel."""
    s.flat(s.rect(x, y, x + w, y + h), iris)
    s.flat(s.rect(x, y, x + w, y + 1), '#120d1c')
    if shine:
        s.flat(s.px([(x, y + 1)]), '#ffffff')
