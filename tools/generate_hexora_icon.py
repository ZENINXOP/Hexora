"""Create density assets from the owner-supplied Hexora artwork.

The original remains in docs/branding/hexora-icon.png; only size/padding
variants are generated. Requires Pillow. AI-assisted integration: OpenAI Codex.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def generate():
    with Image.open(ROOT / 'docs/branding/hexora-icon.png') as original:
        original = original.convert('RGB')
        for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96),
                              ('xxhdpi', 144), ('xxxhdpi', 192)]:
            original.resize((size, size), Image.Resampling.LANCZOS).save(
                ROOT / f'app/src/main/res/mipmap-{density}/ic_launcher.png')
        # Additional padding keeps the folder and wordmark within launcher masks.
        canvas = Image.new('RGB', (864, 864), '#0B0C0E')
        canvas.paste(original.resize((760, 760), Image.Resampling.LANCZOS), (52, 52))
        target = ROOT / 'app/src/main/res/drawable-nodpi/hexora_launcher_artwork.png'
        target.parent.mkdir(exist_ok=True)
        canvas.save(target, optimize=True)
    print('Generated launcher density assets from the supplied artwork.')


if __name__ == '__main__':
    generate()
