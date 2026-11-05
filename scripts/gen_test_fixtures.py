# Test image generation + engine smoke test fixture.
# Generates the qa-checklist §2 test set into test/fixtures (gitignored).
from PIL import Image, ImageDraw, ImageFilter
import os, random

OUT = os.path.join(os.path.dirname(__file__), '..', 'test', 'fixtures')
os.makedirs(OUT, exist_ok=True)
random.seed(7)

def gradient_photo(w, h, name, noise=6):
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = (int(255 * x / w), int(255 * y / h), 128 + random.randint(-noise, noise))
    img.save(os.path.join(OUT, name), quality=95)
    return img

# 1) small old photo (240p-ish)
gradient_photo(320, 240, 'small_photo.jpg')
# 2) noisy night photo
img = gradient_photo(400, 300, 'noisy_night.jpg', noise=40)
# 3) anime illustration (flat colors, sharp edges)
img = Image.new('RGB', (360, 480), (250, 240, 235))
d = ImageDraw.Draw(img)
d.ellipse([90, 60, 270, 240], fill=(90, 60, 160), outline=(40, 20, 80), width=6)
d.polygon([(80, 460), (180, 240), (280, 460)], fill=(240, 120, 140), outline=(150, 40, 70), width=5)
d.rectangle([20, 420, 340, 470], fill=(80, 160, 220))
img.save(os.path.join(OUT, 'anime.png'))
# 4) PNG with transparency
img = Image.new('RGBA', (300, 300), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.ellipse([20, 20, 280, 280], fill=(79, 70, 229, 255))
d.ellipse([90, 90, 210, 210], fill=(124, 58, 237, 180))
img.save(os.path.join(OUT, 'transparent.png'))
# 5) screenshot with text
img = Image.new('RGB', (420, 720), (245, 245, 248))
d = ImageDraw.Draw(img)
for i in range(14):
    y = 30 + i * 48
    d.rectangle([24, y, 396, y + 30], fill=(255, 255, 255), outline=(220, 222, 228))
    d.rectangle([36, y + 8, 40 + i * 12, y + 22], fill=(79, 70, 229))
img.save(os.path.join(OUT, 'screenshot.png'))
# 6) large JPEG > 2 MB (noise compresses poorly)
big = Image.new('RGB', (2200, 1600))
px = big.load()
for y in range(0, 1600, 2):
    for x in range(0, 2200, 2):
        v = random.randint(0, 255)
        for dy in range(2):
            for dx in range(2):
                px[x + dx, y + dy] = (v, v, v)
big.save(os.path.join(OUT, 'large_5mb.jpg'), quality=95)
# 7) small JPEG ~1.5MB? (generated on demand in tests instead)

# engine smoke fixture: 128x128 gradient
gradient_photo(128, 128, 'engine_smoke.png')

print('fixtures in', os.path.abspath(OUT))
for f in sorted(os.listdir(OUT)):
    print(' ', f, os.path.getsize(os.path.join(OUT, f)) // 1024, 'KB')
