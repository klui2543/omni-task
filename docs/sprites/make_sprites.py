"""Rebuilds docs/sprites/*.png (1x pixel sprites). Usage: python3 make_sprites.py [id ...]"""
import os, sys
from PIL import Image
import mascots
try:
    import humans
    ALL = {**mascots.ALL, **humans.ALL}
except ImportError:
    ALL = dict(mascots.ALL)

here = os.path.dirname(os.path.abspath(__file__))
ids = sys.argv[1:] or list(ALL)
for i in ids:
    ALL[i]().render().save(os.path.join(here, i + '.png'))
# contact sheet for a quick look (4x)
S = 5
sheet = Image.new('RGBA', (len(ids) * S * 82, S * 122), (14, 15, 20, 255))
x = 0
for i in ids:
    im = Image.open(os.path.join(here, i + '.png'))
    im = im.resize((im.width * S, im.height * S), Image.NEAREST)
    sheet.alpha_composite(im, (x, 0)); x += S * 82
sheet.save(os.path.join(os.environ.get('SHEET_DIR', here), 'sheet.png'))
