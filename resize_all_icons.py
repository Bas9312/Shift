#!/usr/bin/env python3
# resize_to_square.py
from pathlib import Path
from PIL import Image, ImageOps

SIZE = 128
INPUT = Path("icons")
OUTPUT = Path("icons_resized")

def process_png(fp: Path):
    img = Image.open(fp).convert("RGBA")

    # Обрезаем прозрачные поля по альфа-каналу
    alpha = img.getchannel("A")
    bbox = alpha.getbbox()  # прямоугольник ненулевой альфы, None если пусто
    if bbox:
        img = img.crop(bbox)

    # Совместимость с разными версиями Pillow
    try:
        RESAMPLE = Image.Resampling.LANCZOS
    except AttributeError:
        RESAMPLE = Image.LANCZOS

    # Умещаем в рамку SIZE×SIZE без искажения пропорций
    fitted = ImageOps.contain(img, (SIZE, SIZE), method=RESAMPLE)

    # Центрируем на прозрачном холсте нужного размера
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    x = (SIZE - fitted.width) // 2
    y = (SIZE - fitted.height) // 2
    canvas.paste(fitted, (x, y), fitted)

    # Сохраняем тем же именем в icons_resized
    out_path = OUTPUT / fp.name
    canvas.save(out_path, optimize=True)

def main():
    OUTPUT.mkdir(exist_ok=True)
    files = sorted(p for p in INPUT.glob("*.png") if p.is_file())
    if not files:
        print("В папке ./icons PNG не найдены")
        return
    for p in files:
        process_png(p)
        print(f"✔ {p.name}")
    print(f"Готово: {len(files)} файлов → {OUTPUT.resolve()}")

if __name__ == "__main__":
    main()
