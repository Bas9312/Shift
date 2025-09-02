from pathlib import Path
from PIL import Image
import numpy as np

sheet = Image.open("spritesheet.png").convert("RGBA")
alpha = np.array(sheet)[:, :, 3]

rows = np.where(~np.all(alpha == 0, axis=1))[0]
cols = np.where(~np.all(alpha == 0, axis=0))[0]

def regions(idxs):
    if not len(idxs): return []
    blocks, start = [], idxs[0]
    for prev, i in zip(idxs, idxs[1:]):
        if i != prev + 1:
            blocks.append((start, prev))
            start = i
    blocks.append((start, idxs[-1]))
    return blocks

row_blocks = regions(rows)
col_blocks = regions(cols)

out = Path("icons")
out.mkdir(exist_ok=True)
counter = 0
for ry0, ry1 in row_blocks:
    for cx0, cx1 in col_blocks:
        # +1 потому что верхняя/левая граница включены
        crop = sheet.crop((cx0, ry0, cx1 + 1, ry1 + 1))
        crop.save(out / f"icon_{counter:02}.png")
        counter += 1
print(f"Сохранено {counter} файлов в папке {out}")
