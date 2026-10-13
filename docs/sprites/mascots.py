from pix import *

W = H = 64


def halo(s, T, base=56, cx=32):
    """A bed of flames rising behind the body, leaning away from the middle."""
    for dx, h, w, sw, ph in [(-20, 30, 12, -1, 0.3), (-11, 42, 15, -1, 1.2), (0, 48, 17, 1, 2.1), (11, 40, 15, 1, 0.7), (20, 28, 12, 1, 1.9)]:
        s.flame(cx + dx, base, h, w, T, sway=sw, phase=ph)


def blush(s, pts):
    s.flat(s.px(pts), '#ff8fa3')


def homura():
    s = Sprite(W, H); T = '#ff8a5b'
    halo(s, T)
    # tail with a flame tip
    s.add(s.line([(42, 50), (52, 49), (56, 42)], 4), T, edge=True)
    s.add(s.poly([(14, 38), (4, 28), (6, 42), (13, 45)]), '#ffc83d', edge=True)
    s.add(s.poly([(50, 38), (60, 28), (58, 42), (51, 45)]), '#ffc83d', edge=True)
    s.add(s.ell(32, 44, 11, 10), T)
    s.add(s.ell(32, 46, 7, 7), '#ffe0a8')
    s.add(s.ell(25, 53, 5, 3), T); s.add(s.ell(39, 53, 5, 3), T)
    s.add(s.ell(32, 28, 15, 13), T)
    s.add(s.poly([(20, 20), (18, 8), (27, 16)]), '#ffd166', edge=True)
    s.add(s.poly([(44, 20), (46, 8), (37, 16)]), '#ffd166', edge=True)
    s.add(s.ell(32, 33, 8, 5), '#ffe0a8', rim=False)
    eye(s, 23, 26, h=4); eye(s, 39, 26, h=4)
    blush(s, [(21, 31), (22, 31), (42, 31), (43, 31)])
    s.flat(s.px([(30, 34), (31, 35), (32, 35), (33, 35), (34, 34)]), '#7a2e1b')
    s.flat(s.px([(31, 33)]), '#7a2e1b')
    s.flame(32, 22, 11, 7, T, where='top', sway=1)
    return s


def mimi():
    s = Sprite(W, H); T = '#8c93ff'
    halo(s, T)
    s.add(s.ell(17, 40, 4, 10), '#6a72e8', edge=True)
    s.add(s.ell(47, 40, 4, 10), '#6a72e8', edge=True)
    s.add(s.poly([(20, 26), (19, 12), (28, 21)]), T, edge=True)
    s.add(s.poly([(44, 26), (45, 12), (36, 21)]), T, edge=True)
    s.add(s.ell(32, 38, 14, 17), T)
    s.add(s.ell(32, 44, 9, 10), '#e1e4ff')
    s.add(s.rect(26, 54, 30, 58), '#ffd166'); s.add(s.rect(34, 54, 38, 58), '#ffd166')
    s.flat(s.ell(25, 31, 6, 6), '#ffffff'); s.flat(s.ell(39, 31, 6, 6), '#ffffff')
    s.flat(s.ring(25, 31, 6.4, 6.4, 1.6), '#ffc83d'); s.flat(s.ring(39, 31, 6.4, 6.4, 1.6), '#ffc83d')
    s.flat(s.rect(30, 30, 34, 32), '#ffc83d')
    eye(s, 24, 29, h=4); eye(s, 38, 29, h=4)
    s.add(s.poly([(32, 38), (29, 35), (35, 35)]), '#ffd166', shade=False)
    s.spark([(32, 6), (32, 7), (32, 8), (31, 7), (33, 7)], '#ffe27a')
    s.flame(32, 25, 10, 6, T, where='top', sway=1)
    return s


def fung():
    s = Sprite(W, H); T = '#67d6b5'
    halo(s, T)
    for cx, cy, rx, ry in [(46, 44, 7, 6), (53, 37, 6, 6), (55, 47, 5, 5), (45, 52, 6, 4)]:
        s.add(s.ell(cx, cy, rx, ry), '#eafff7')
    s.add(s.ell(30, 46, 10, 8), T)
    s.add(s.ell(26, 55, 4, 3), T); s.add(s.ell(35, 55, 4, 3), T)
    s.add(s.poly([(19, 28), (18, 10), (28, 22)]), T, edge=True)
    s.add(s.poly([(41, 28), (42, 10), (32, 22)]), T, edge=True)
    s.flat(s.poly([(21, 25), (21, 15), (26, 22)]), '#bdf3e2'); s.flat(s.poly([(39, 25), (39, 15), (34, 22)]), '#bdf3e2')
    s.add(s.ell(30, 33, 13, 11), T)
    s.add(s.poly([(17, 38), (22, 34), (24, 40)]), '#eafff7', shade=False)
    s.add(s.poly([(43, 38), (38, 34), (36, 40)]), '#eafff7', shade=False)
    s.add(s.ell(30, 38, 7, 4), '#eafff7', rim=False)
    eye(s, 22, 31, w=3, h=3); eye(s, 35, 31, w=3, h=3)
    s.flat(s.rect(29, 36, 32, 38), '#2a1f3a')
    s.flat(s.px([(30, 40), (29, 39), (31, 39)]), '#2a1f3a')
    blush(s, [(20, 36), (21, 36), (39, 36), (40, 36)])
    s.flame(30, 24, 10, 6, T, where='top', sway=1)
    return s


def puifong():
    s = Sprite(W, H); T = '#5cc8ff'
    halo(s, T)
    for cx, cy, rx, ry in [(14, 52, 8, 4), (32, 55, 10, 4), (50, 52, 8, 4)]:
        s.add(s.ell(cx, cy, rx, ry), '#f3fbff', shade=False)
    s.add(s.poly([(46, 32), (54, 26), (60, 16), (60, 30), (62, 40), (54, 38)]), T, edge=True)
    s.add(s.ell(30, 34, 22, 15), T)
    s.add(s.ell(28, 42, 16, 6), '#d6f3ff', rim=False)
    s.add(s.poly([(26, 46), (34, 46), (30, 52)]), '#4aa9e0', edge=True)
    eye(s, 16, 29, w=3, h=4); eye(s, 28, 29, w=3, h=4)
    blush(s, [(13, 35), (14, 35), (32, 35), (33, 35)])
    s.flat(s.px([(21, 37), (22, 38), (23, 38), (24, 37)]), '#2a4a7a')
    s.flat(s.px([(22, 12), (22, 13), (21, 14), (23, 14), (22, 15), (22, 16), (22, 17)]), '#bfeaff')
    s.flat(s.px([(30, 15), (30, 16)]), '#bfeaff')
    s.flame(22, 20, 10, 6, T, where='top', sway=1)
    return s


def tonkla():
    s = Sprite(W, H); T = '#8bd17c'
    halo(s, T)
    br = '#8a6238'
    s.add(s.line([(24, 24), (21, 14), (16, 9)], 2), br, edge=True, shade=False)
    s.add(s.line([(21, 14), (22, 6)], 2), br, edge=True, shade=False)
    s.add(s.line([(40, 24), (43, 14), (48, 9)], 2), br, edge=True, shade=False)
    s.add(s.line([(43, 14), (42, 6)], 2), br, edge=True, shade=False)
    for x, y in [(15, 8), (22, 5), (49, 8), (42, 5)]:
        s.add(s.ell(x, y, 3, 3), T, edge=True)
    s.add(s.ell(32, 52, 9, 6), '#d9b982')
    s.add(s.ell(25, 57, 3, 2.5), '#8a6238'); s.add(s.ell(39, 57, 3, 2.5), '#8a6238')
    s.add(s.ell(17, 29, 5, 6), '#d9b982', edge=True); s.add(s.ell(47, 29, 5, 6), '#d9b982', edge=True)
    s.add(s.ell(32, 36, 15, 14), '#d9b982')
    s.add(s.ell(32, 42, 7, 5), '#f6ead0', rim=False)
    s.flat(s.px([(27, 28), (28, 28), (31, 25), (32, 25), (36, 28), (37, 28)]), '#f6ead0')
    eye(s, 23, 34, h=4); eye(s, 39, 34, h=4)
    s.flat(s.rect(31, 40, 34, 42), '#2a1f3a')
    s.flat(s.px([(31, 44), (32, 45), (33, 45), (34, 44)]), '#2a1f3a')
    blush(s, [(20, 39), (21, 39), (43, 39), (44, 39)])
    s.flame(32, 27, 10, 6, T, where='top', sway=1)
    return s


ALL = {'homura': homura, 'mimi': mimi, 'fung': fung, 'puifong': puifong, 'tonkla': tonkla}
