"""Leo, fire tribe swordsman. Fighting-game idle sprite, three-quarter view facing right."""
import sys
from PIL import Image
from pix import *


def patch(s, x0, y0, rows, pal):
    """Hand-placed pixels: one string per row, '.' leaves the pixel alone."""
    for ch, col in pal.items():
        pts = [(x0 + x, y0 + y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == ch]
        if pts:
            s.flat(s.px(pts), col)


def leo():
    s = Sprite(48, 72)
    skin = '#f8d2ae'
    hair = '#d8362c'
    tunic, tunic2 = '#f28a32', '#c45a1c'
    gold, cloth = '#f2c14e', '#f3ead6'
    pants, boot, belt = '#454160', '#8a5230', '#4a2a1a'
    steel = '#dfe6f2'

    # sword in the back hand, point down behind him
    s.add(s.poly([(8, 47), (11, 48), (5, 67), (3, 67)]), steel, edge=True)
    s.flat(s.line([(9, 49), (5, 64)], 1), '#9aa6bc')
    s.add(s.poly([(5, 44), (14, 46), (13, 48), (4, 46)]), gold, edge=True)

    # back leg, front leg, boots
    s.add(s.poly([(18, 47), (24, 47), (19, 57), (13, 56)]), pants, edge=True)
    s.add(s.poly([(13, 56), (19, 57), (16, 65), (11, 65)]), pants, edge=True)
    s.add(s.poly([(25, 47), (31, 47), (34, 57), (28, 57)]), pants, edge=True)
    s.add(s.poly([(28, 57), (34, 57), (36, 65), (31, 65)]), pants, edge=True)
    s.add(s.poly([(10, 63), (17, 63), (17, 69), (6, 69), (7, 66)]), boot, edge=True)
    s.add(s.poly([(30, 63), (37, 63), (40, 66), (42, 69), (30, 69)]), boot, edge=True)

    # back arm reaching down to the hilt
    s.add(s.line([(16, 31), (12, 39), (9, 45)], 4), skin)
    s.add(s.line([(12, 39), (9, 45)], 4), tunic2, edge=True)
    s.add(s.ell(9, 46, 2.5, 2.5), skin)

    # scarf tail flying back, torso, skirt flaps, belt
    s.add(s.poly([(19, 28), (11, 32), (6, 37), (9, 38), (14, 35), (20, 31)]), cloth, edge=True)
    s.add(s.poly([(15, 29), (31, 29), (33, 35), (30, 44), (18, 44), (15, 37)]), tunic, edge=True)
    s.add(s.poly([(17, 45), (23, 45), (22, 52), (16, 51)]), tunic2, edge=True)
    s.add(s.poly([(25, 45), (31, 45), (33, 51), (27, 52)]), tunic2, edge=True)
    s.add(s.poly([(17, 43), (31, 43), (31, 46), (17, 46)]), belt, edge=True)
    s.flat(s.rect(26, 43, 29, 46), gold)
    s.add(s.poly([(20, 30), (27, 30), (24, 37)]), cloth, edge=True)           # shirt in the collar
    s.add(s.ell(15.5, 31, 4, 3.2), gold, edge=True)                             # shoulder guard
    s.add(s.poly([(19, 26), (28, 26), (29, 30), (18, 30)]), cloth, edge=True)   # scarf round the neck

    # neck, face
    s.add(s.rect(21, 22, 26, 27), skin)
    s.add(s.poly([(17, 12), (30, 12), (31, 18), (29, 22), (26, 25), (21, 24), (18, 20)]), skin)
    s.add(s.ell(17.5, 18, 1.8, 2.2), skin, edge=True)                          # ear

    # hair: spikes sweep back to the left, bangs over the brow
    spikes = [(12, 22), (11, 17), (4, 14), (11, 12), (6, 5), (14, 8), (15, 0), (20, 6), (25, 1), (27, 7), (34, 3),
              (31, 10), (38, 10), (32, 13), (33, 17), (30, 14), (17, 14), (15, 21)]
    s.add(s.poly(spikes), hair, edge=True)
    s.add(s.poly([(18, 12), (20, 18), (22, 12)]), hair, edge=True)
    s.add(s.poly([(22, 12), (25, 17), (27, 12)]), hair, edge=True)
    s.add(s.poly([(27, 12), (30, 16), (31, 12)]), hair, edge=True)
    s.add(s.poly([(14, 15), (13, 23), (17, 17)]), hair, edge=True)
    # headband and its tails
    band = '#3d5aa8'
    s.add(s.poly([(15, 12), (32, 11), (32, 13), (15, 14)]), band, edge=True)
    s.add(s.poly([(15, 12), (8, 15), (3, 21), (6, 21), (10, 17), (15, 14)]), band, edge=True)

    # face details, by hand
    patch(s, 19, 15, [
        "KKK..KKK",
        "wEe..wEe",
        "eLE..eLE",
        ".E....E.",
        "..........S",
        ".....MM",
    ], {'K': '#26101a', 'w': '#ffffff', 'e': '#8c1d1d', 'E': '#5a1014', 'L': '#e0503a', 'S': '#dba07e', 'M': '#7a2420'})
    patch(s, 18, 14, ["KK....KKK"], {'K': '#7a1a14'})                        # brows
    patch(s, 19, 20, ["p......p"], {'p': '#f3a08e'})                            # blush
    patch(s, 22, 5, ["a", ".a", "..aa", "...a"], {'a': '#ff7a58'})              # hair shine
    patch(s, 12, 9, ["a", "aa", ".a"], {'a': '#ff7a58'})

    # front arm: guard up, fist forward, gauntlet
    s.add(s.line([(31, 31), (36, 38), (38, 34)], 4), skin)
    s.add(s.line([(36, 38), (38, 34)], 4), tunic2, edge=True)
    s.add(s.ell(38.5, 32.5, 2.8, 2.6), skin)
    s.add(s.ell(31, 31, 3, 2.6), tunic, edge=True)

    # flames on the brow and the fist
    s.flame(24, 13, 10, 7, '#ffb53a', where='top', sway=1)
    s.flame(39, 31, 11, 7, '#ffb53a', where='top', sway=1, phase=1.5)
    return s


if __name__ == '__main__':
    im = leo().render()
    im.save('leo_idle.png')
    k = int(sys.argv[1]) if len(sys.argv) > 1 else 8
    bg = Image.new('RGBA', (im.width * k + 40, im.height * k + 40), (104, 132, 92, 255))
    bg.alpha_composite(im.resize((im.width * k, im.height * k), Image.NEAREST), (20, 20))
    bg.save(sys.argv[2] if len(sys.argv) > 2 else 'leo_preview.png')
