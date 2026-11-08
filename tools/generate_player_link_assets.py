#!/usr/bin/env python3
import json
import os
import random

from PIL import Image

from generate_assets import CELL, FACES, RES, box, box_mapped, model, render_icon

ITEMS = os.path.join(RES, "Common", "Items", "TaleOS")
ICONS = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
NAME = "TaleOS_Player_Link"


def player_link_model():
    board = {face: "keys" for face in FACES}
    board["top"] = "wood"
    nodes = [
        box_mapped(10, "CircuitBoard", (24, 3, 28), (0, 1.5, 0), board),
        box(11, "CopperRailLeft", (2, 3, 24), (-11, 4, 0), "brass"),
        box(12, "CopperRailRight", (2, 3, 24), (11, 4, 0), "brass"),
        box(13, "IdentityCore", (16, 3, 19), (0, 5, 1), "keys"),
        box(14, "PlayerHead", (6, 4, 6), (0, 8.5, 5), "screen"),
        box(15, "PlayerBody", (11, 3, 8), (0, 8, -5), "screen"),
        box(16, "LinkNodeLeft", (3, 3, 3), (-7, 8, 5), "brass"),
        box(17, "LinkNodeRight", (3, 3, 3), (7, 8, 5), "brass"),
        box(18, "StatusLight", (4, 2, 2), (0, 5, 13), "screen"),
    ]
    for index, x in enumerate((-7, 0, 7)):
        nodes.append(box(20 + index, "Connector" + str(index), (3, 2, 4), (x, 2, 15), "brass"))
    return model(nodes)


def texture(path):
    rng = random.Random(1201)
    image = Image.new("RGB", (64, 64))
    palettes = {
        "brass": (190, 125, 72),
        "wood": (30, 72, 83),
        "screen": (94, 218, 224),
        "keys": (20, 29, 40),
    }
    for cell, (u0, v0) in CELL.items():
        base = palettes[cell]
        for dy in range(32):
            for dx in range(32):
                factor = 0.76 if dx in (0, 31) or dy in (0, 31) else 1.0
                if cell == "wood":
                    if dx % 8 in (0, 1) or dy % 8 in (0, 1):
                        factor *= 1.22
                    if (dx in (5, 26) and 5 < dy < 27) or (dy in (5, 26) and 5 < dx < 27):
                        factor *= 0.72
                elif cell == "screen":
                    factor *= 0.88 + 0.12 * (dy / 31)
                    if dx in (7, 24) or dy in (7, 24):
                        factor *= 0.62
                elif cell == "keys" and (dx % 5 == 0 or dy % 5 == 0):
                    factor *= 1.18
                noise = rng.uniform(-3, 3)
                color = tuple(max(0, min(255, int(channel * factor + noise))) for channel in base)
                image.putpixel((u0 + dx, v0 + dy), color)
    image.save(path)


def main():
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)
    model_path = os.path.join(ITEMS, NAME + ".blockymodel")
    texture_path = os.path.join(ITEMS, NAME + "_Texture.png")
    with open(model_path, "w") as output:
        json.dump(player_link_model(), output, separators=(",", ":"))
    texture(texture_path)
    render_icon(model_path, texture_path, os.path.join(ICONS, NAME + ".png"))
    print("generado", NAME)


if __name__ == "__main__":
    main()
