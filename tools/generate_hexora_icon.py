"""Render Hexora's code-defined vector artwork for pre-adaptive launchers.

Requires Pillow. Keep geometry aligned with res/drawable/ic_hexora_foreground.xml.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BACKGROUND = '#303F75'
OUTER = [(54, 23), (81, 38.5), (81, 69.5), (54, 85), (27, 69.5), (27, 38.5)]
INNER = [(54, 29), (76, 41.5), (76, 66.5), (54, 79), (32, 66.5), (32, 41.5)]
LETTER = [(40, 39), (47, 39), (47, 50.5), (61, 50.5), (61, 39), (68, 39),
          (68, 69), (61, 69), (61, 57.5), (47, 57.5), (47, 69), (40, 69)]

def render(size):
    scale = size * 4 / 108
    image = Image.new('RGBA', (size * 4, size * 4))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1),
                           radius=20 * scale, fill=BACKGROUND)
    for points, color in ((OUTER, '#93B8FF'), (INNER, BACKGROUND), (LETTER, '#FFFFFF')):
        draw.polygon([(x * scale, y * scale) for x, y in points], fill=color)
    return image.resize((size, size), Image.Resampling.LANCZOS)

if __name__ == '__main__':
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96),
                          ('xxhdpi', 144), ('xxxhdpi', 192)]:
        render(size).save(ROOT / f'app/src/main/res/mipmap-{density}/ic_launcher.png')
    brand = ROOT / 'docs/branding'
    brand.mkdir(parents=True, exist_ok=True)
    render(512).save(brand / 'hexora-icon.png')
    print('Rendered launcher assets and docs/branding/hexora-icon.png')
