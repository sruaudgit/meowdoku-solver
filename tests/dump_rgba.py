# Extrait les pixels RGB de l'image sample en un buffer brut (3 octets/pixel)
# utilisé par tests/deno_test.js. Nécessite Pillow : pip install pillow
# Usage : python tests/dump_rgba.py
from PIL import Image

SRC = r'C:\Users\notre\Documents\Code\web\meowdoku-solver\Samples\Screenshot_20260903_111912_Meowdoku.jpg'
OUT = r'C:\Users\notre\Documents\Code\web\meowdoku-solver\tests\sample.rgba'

img = Image.open(SRC).convert('RGB')
W = 1080
H = round(img.height * (W / img.width))
img = img.resize((W, H), Image.LANCZOS)
with open(OUT, 'wb') as fp:
    fp.write(img.tobytes())
print('OK', W, H)
