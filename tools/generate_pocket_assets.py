#!/usr/bin/env python3
import json
import os
import random

from PIL import Image

from generate_assets import CELL, FACES, RES, box, box_mapped, model, render_icon

ITEMS = os.path.join(RES, "Common", "Items", "TaleOS")
ICONS = os.path.join(RES, "Common", "Icons", "ItemsGenerated")


def pocket_model():
    screen = {face: "brass" for face in FACES}
    screen["front"] = "screen"
    deck = {face: "wood" for face in FACES}
    deck["top"] = "keys"
    return model([
        box_mapped(10, "KeyboardDeck", (26, 4, 19), (0, 2, 4), deck),
        box_mapped(11, "ScreenShell", (26, 19, 3), (0, 11.5, -5), screen),
        box(12, "HingeLeft", (5, 3, 4), (-9, 4, -4), "brass"),
        box(13, "HingeRight", (5, 3, 4), (9, 4, -4), "brass"),
        box(14, "Trackpad", (7, 1, 5), (0, 4.5, 9), "screen"),
    ])


def texture(path):
    rng = random.Random(941)
    image = Image.new("RGB", (64, 64))
    palettes = {
        "brass": (102, 113, 142),
        "wood": (44, 51, 72),
        "screen": (30, 183, 170),
        "keys": (25, 29, 43),
    }
    for cell, (u0, v0) in CELL.items():
        base = palettes[cell]
        for dy in range(32):
            for dx in range(32):
                factor = 0.72 if dx in (0, 31) or dy in (0, 31) else 1.0
                if cell == "screen" and dy % 4 == 0:
                    factor *= 0.82
                if cell == "keys" and (dx % 5 == 0 or dy % 5 == 0):
                    factor *= 1.35
                noise = rng.uniform(-4, 4)
                color = tuple(max(0, min(255, int(channel * factor + noise))) for channel in base)
                image.putpixel((u0 + dx, v0 + dy), color)
    image.save(path)


def main():
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)
    name = "TaleOS_PocketComputer"
    model_path = os.path.join(ITEMS, name + ".blockymodel")
    texture_path = os.path.join(ITEMS, name + "_Texture.png")
    with open(model_path, "w") as output:
        json.dump(pocket_model(), output, separators=(",", ":"))
    texture(texture_path)
    render_icon(model_path, texture_path, os.path.join(ICONS, name + ".png"))
    print("generado", name)


if __name__ == "__main__":
    main()
