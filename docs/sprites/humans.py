from pix import *


def leo():
    """Leo, fire tribe swordsman. Anime proportions (about seven heads), one flame on the brow, one on the blade."""
    s = Sprite(80, 120)
    T = '#ff6a3a'
    skin, hair, hair2 = '#f6cfa6', '#c42a2c', '#8f1d24'
    coat, coat2, trim = '#f08a2e', '#c4601c', '#6b2a18'
    pants, boot, steel = '#3a3347', '#7a4626', '#d5dcea'

    # aura: a bed of flames behind the whole body, leaning away from the middle
    for dx, h, w, sw, ph in [(-27, 36, 13, -1, .2), (-18, 54, 16, -1, 1.1), (-9, 70, 18, -1, 2.0), (0, 82, 20, 1, .6),
                             (9, 68, 18, 1, 1.7), (18, 52, 16, 1, 2.4), (27, 34, 13, 1, .9)]:
        s.flame(40 + dx, 104, h, w, T, sway=sw, phase=ph)

    # sword arm back, blade
    blade = s.poly([(61, 56), (63, 56), (70, 14), (66, 10), (63, 16)])
    s.add(blade, steel, edge=True)
    s.flat(s.line([(64, 50), (68, 20)], 1), '#a8b3c8')
    s.add(s.rect(57, 54, 65, 57), '#e8b84a', edge=True)           # crossguard
    s.add(s.rect(60, 57, 63, 66), '#6b2a18', edge=True)           # grip
    s.add(s.ell(61.5, 67, 2.4, 2.2), '#e8b84a')                    # pommel

    # legs (left one planted, right one stepping out)
    s.add(s.poly([(32, 58), (40, 58), (40, 100), (31, 100)]), pants, edge=True)
    s.add(s.poly([(40, 58), (50, 58), (54, 100), (45, 100)]), pants, edge=True)
    # boots
    s.add(s.poly([(30, 92), (40, 92), (41, 104), (35, 107), (26, 107), (27, 103)]), boot, edge=True)
    s.add(s.poly([(44, 92), (55, 92), (58, 103), (62, 107), (50, 107), (46, 104)]), boot, edge=True)
    s.flat(s.rect(30, 92, 40, 95), '#a8683a'); s.flat(s.rect(44, 92, 55, 95), '#a8683a')

    # coat tails behind the belt, torso, collar
    s.add(s.poly([(29, 56), (40, 56), (39, 70), (27, 70)]), coat2, edge=True)
    s.add(s.poly([(40, 56), (51, 56), (53, 70), (41, 70)]), coat2, edge=True)
    s.add(s.poly([(30, 34), (50, 34), (52, 50), (49, 58), (31, 58), (28, 50)]), coat, edge=True)
    s.add(s.poly([(36, 33), (44, 33), (40, 46)]), '#f8efe0', edge=True)        # shirt in the V of the coat
    s.add(s.rect(29, 55, 51, 59), trim, edge=True)                               # belt
    s.add(s.rect(38, 55, 42, 59), '#e8b84a')                                     # buckle
    
    # neck and head
    s.add(s.rect(37, 28, 43, 35), skin)
    s.add(s.poly([(31, 19), (49, 19), (47, 27), (42, 31), (38, 31), (33, 27)]), skin)
    s.add(s.ell(40, 20, 9, 10), skin)
    # face
    for x in (34, 43):
        s.flat(s.rect(x, 21, x + 4, 25), '#ffffff')
        s.flat(s.rect(x + 1, 21, x + 3, 25), '#8c1d1d')
        s.flat(s.rect(x, 20, x + 4, 21), '#2a1218')
        s.flat(s.px([(x + 1, 22)]), '#ffffff')
    s.flat(s.line([(33, 19), (38, 18)], 1), '#7a1a14'); s.flat(s.line([(42, 18), (47, 19)], 1), '#7a1a14')
    s.flat(s.px([(40, 26)]), '#d99c76')
    s.flat(s.line([(37, 28), (42, 28), (44, 27)], 1), '#8a3a2a')               # smirk
    s.flat(s.px([(33, 25), (47, 25)]), '#f09a8a')

    # hair: spiky mass, fringe over the brow
    s.add(s.ell(40, 11, 12, 8), hair, edge=True)
    for pts in [[(29, 14), (25, 4), (34, 10)], [(33, 9), (33, -0 + 1), (40, 7)], [(39, 8), (43, 0), (47, 8)],
                [(46, 9), (54, 3), (51, 14)], [(50, 12), (58, 12), (51, 20)], [(30, 18), (23, 20), (30, 26)]]:
        s.add(s.poly(pts), hair, edge=True)
    s.add(s.poly([(30, 13), (40, 11), (50, 13), (50, 17), (46, 19), (44, 15), (40, 17), (36, 15), (34, 19), (31, 18)]), hair, edge=True)
    s.flat(s.line([(34, 12), (38, 10)], 1), hair2); s.flat(s.line([(44, 10), (48, 12)], 1), hair2)
    s.add(s.poly([(29, 20), (31, 27), (33, 20)]), hair, edge=True)
    s.add(s.poly([(51, 20), (49, 27), (47, 20)]), hair, edge=True)

    # arms: left fist down with a flame gauntlet, right hand on the grip
    s.add(s.line([(30, 37), (25, 47), (25, 56)], 5), '#f2e6d0', edge=True)
    s.add(s.rect(22, 52, 29, 60), coat2, edge=True)
    s.add(s.ell(25.5, 62, 3.6, 3.4), skin)
    s.add(s.line([(50, 37), (56, 46), (61, 58)], 5), '#f2e6d0', edge=True)
    s.add(s.ell(61.5, 60, 3.5, 3.5), skin)
    s.add(s.ell(30, 36, 6, 4.5), '#e8b84a', edge=True)                           # shoulder guard
    s.add(s.ell(50, 36, 4, 3.5), coat2, edge=True)

    # flames you see: the brow, the fist, along the blade
    s.flame(40, 17, 13, 9, '#ffb53a', where='top', sway=1)
    s.flame(25, 70, 14, 9, '#ffb53a', where='top', sway=-1, phase=.8)
    s.flame(67, 40, 20, 8, '#ffb53a', where='top', sway=1, phase=2)
    return s


ALL = {'leo': leo}
