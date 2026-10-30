#!/usr/bin/env python3
import json
import os
import random

import numpy as np
from PIL import Image

from generate_assets import BLOCKS, CELL, FACES, RES, box, box_mapped, model, render_icon

ITEMS = os.path.join(RES, "Common", "Items", "TaleOS")
ICONS = os.path.join(RES, "Common", "Icons", "ItemsGenerated")


def pendrive_model():
    faces = {face: "keys" for face in FACES}
    faces["top"] = "screen"
    return model([
        box_mapped(10, "Body", (16, 5, 23), (0, 4, -2), faces),
        box(11, "Connector", (11, 3, 11), (0, 4, 15), "wood"),
        box(12, "ContactLeft", (3, 1, 6), (-3, 5.5, 16), "brass"),
        box(13, "ContactRight", (3, 1, 6), (3, 5.5, 16), "brass"),
    ])


def reader_model():
    body = {face: "keys" for face in FACES}
    body["top"] = "brass"
    return model([
        box(10, "Base", (30, 5, 28), (0, 2.5, 0), "wood"),
        box_mapped(11, "ReaderBody", (28, 13, 25), (0, 11.5, -1), body),
        box(12, "UsbSlot", (19, 3, 2), (-2, 11, 12.5), "screen"),
        box(13, "StatusLight", (3, 3, 2), (10, 11, 12.5), "wood"),
        box(14, "TopAccent", (23, 1, 18), (0, 18.5, -1), "screen"),
    ])


def texture(path, pendrive=False):
    rng = random.Random(730 if pendrive else 731)
    image = Image.new("RGB", (64, 64))
    palettes = {
        "brass": (188, 197, 205) if pendrive else (65, 76, 88),
        "wood": (177, 188, 196) if pendrive else (73, 211, 184),
        "screen": (30, 165, 157) if pendrive else (20, 31, 40),
        "keys": (32, 39, 49),
    }
    for cell, (u0, v0) in CELL.items():
        base = palettes[cell]
        for dy in range(32):
            for dx in range(32):
                factor = 0.78 if dx in (0, 31) or dy in (0, 31) else 1.0
                if cell == "screen" and dy % 4 == 0:
                    factor *= 0.9
                noise = rng.uniform(-4, 4)
                color = tuple(max(0, min(255, int(channel * factor + noise))) for channel in base)
                image.putpixel((u0 + dx, v0 + dy), color)
    image.save(path)


def main():
    os.makedirs(ITEMS, exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)
    jobs = [
        ("TaleOS_PenDrive", pendrive_model, ITEMS, True),
        ("TaleOS_USB_Drive", reader_model, BLOCKS, False),
    ]
    for name, builder, directory, pendrive in jobs:
        model_path = os.path.join(directory, name + ".blockymodel")
        texture_path = os.path.join(directory, name + "_Texture.png")
        with open(model_path, "w") as output:
            json.dump(builder(), output, separators=(",", ":"))
        texture(texture_path, pendrive)
        render_icon(model_path, texture_path, os.path.join(ICONS, name + ".png"))
        print("generado", name)


if __name__ == "__main__":
    main()
