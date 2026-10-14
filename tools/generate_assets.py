#!/usr/bin/env python3
"""Genera los assets propios de TaleCraft (modelos .blockymodel, texturas 64px
paleta con ruido, beep .ogg) — pipeline 100% por script, sin Blockbench ni fal.

Uso (venv de ComfyUI: numpy+Pillow+soundfile):
    ~/hytale/toolkit/ComfyUI/.venv/bin/python tools/generate_assets.py

Escala del modelo: 32 unidades = 1 bloque, bloque ocupa y 0..32, x/z -16..16.
UV: textura 64x64 con 4 celdas de 32x32: brass(0,0), wood(32,0), screen(0,32),
keys(32,32). Cada cara apunta al origen de su celda (unwrapMode custom).
"""
import json
import math
import os
import random

import numpy as np
import soundfile as sf
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources"))
BLOCKS = os.path.join(RES, "Common", "Blocks", "TaleCraft")
SOUNDS = os.path.join(RES, "Common", "Sounds", "TaleCraft")

CELL = {"brass": (0, 0), "wood": (32, 0), "screen": (0, 32), "keys": (32, 32)}
FACES = ["front", "back", "right", "bottom", "left", "top"]


def face_layout(cell):
    u, v = CELL[cell]
    return {f: {"offset": {"x": u, "y": v}, "mirror": {"x": False, "y": False}, "angle": 0}
            for f in FACES}


def box(nid, name, size, center, cell):
    return {
        "id": str(nid),
        "name": name,
        "children": [],
        "position": {"x": center[0], "y": center[1], "z": center[2]},
        "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
        "shape": {
            "type": "box",
            "offset": {"x": 0, "y": 0, "z": 0},
            "stretch": {"x": 1, "y": 1, "z": 1},
            "settings": {"size": {"x": size[0], "y": size[1], "z": size[2]}},
            "visible": True,
            "doubleSided": False,
            "shadingMode": "standard",
            "unwrapMode": "custom",
            "textureLayout": face_layout(cell),
        },
    }


def model(nodes):
    return {
        "nodes": [{
            "id": "1",
            "name": "R-Attachment",
            "children": nodes,
            "position": {"x": 0, "y": 0, "z": 0},
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
            "shape": {"type": "none", "offset": {"x": 0, "y": 0, "z": 0},
                      "stretch": {"x": 1, "y": 1, "z": 1}, "settings": {"isPiece": True},
                      "visible": True, "doubleSided": False, "shadingMode": "standard",
                      "unwrapMode": "custom", "textureLayout": {}},
        }],
        "lod": "auto",
    }


def computer_model():
    # base de madera, monitor latón con pantalla, teclado delante
    return model([
        box(10, "Body", (32, 8, 20), (0, 4, 4), "wood"),
        box(11, "CRT", (24, 18, 16), (0, 17, -4), "brass"),
        box(12, "Screen", (20, 14, 2), (0, 17, 5), "screen"),
        box(13, "Keyboard", (20, 3, 8), (0, 9.5, 9), "keys"),
        box(14, "TopTrim", (26, 2, 18), (0, 27, -4), "brass"),
    ])


def monitor_model():
    # pie, panel latón y pantalla
    return model([
        box(10, "Stand", (8, 12, 4), (0, 6, 0), "brass"),
        box(11, "Panel", (26, 18, 3), (0, 21, 0), "brass"),
        box(12, "Screen", (22, 14, 1.5), (0, 21, 2.5), "screen"),
        box(13, "Foot", (16, 2, 10), (0, 1, 0), "wood"),
    ])


def texture(path):
    """Paleta 64x64 con ruido sutil y borde interior sombreado (estilo Hytale)."""
    rng = random.Random(42)
    img = Image.new("RGB", (64, 64))

    def fill(cell, base, noise=12, border_dark=0.75, gradient=False, grid=None):
        u0, v0 = CELL[cell]
        for dy in range(32):
            for dx in range(32):
                f = 1.0
                if gradient:
                    f *= 1.0 - 0.25 * (dy / 31)          # más oscuro abajo (CRT)
                    if dy % 2 == 0:
                        f *= 0.94                         # scanlines suaves
                if grid and (dx % grid == 0 or dy % grid == 0):
                    f *= 0.6                              # rejilla de teclas
                if dx in (0, 31) or dy in (0, 31):
                    f *= border_dark                      # borde interior
                n = rng.uniform(-noise, noise)
                px = tuple(max(0, min(255, int(c * f + n))) for c in base)
                img.putpixel((u0 + dx, v0 + dy), px)

    fill("brass", (176, 141, 79), noise=10)
    fill("wood", (74, 54, 40), noise=8)
    fill("screen", (22, 34, 46), noise=6, gradient=True)
    fill("keys", (35, 35, 44), noise=6, grid=4)
    img.save(path)


def beep(path):
    """Beep de arranque: 880 Hz (0.09 s) -> 1320 Hz (0.09 s), fade out, 44.1k."""
    rate = 44100
    t1 = np.arange(int(rate * 0.09)) / rate
    t2 = np.arange(int(rate * 0.12)) / rate
    a = np.sin(2 * np.pi * 880 * t1) * 0.35
    b = np.sin(2 * np.pi * 1320 * t2) * 0.35
    fade = np.linspace(1.0, 0.0, len(b)) ** 2
    b = b * fade
    sig = np.concatenate([a, b]).astype(np.float32)
    sf.write(path, sig, rate, format="OGG", subtype="VORBIS")


def main():
    os.makedirs(BLOCKS, exist_ok=True)
    os.makedirs(SOUNDS, exist_ok=True)
    for name, builder in (("TaleCraft_Computer", computer_model), ("TaleCraft_Monitor", monitor_model)):
        with open(os.path.join(BLOCKS, name + ".blockymodel"), "w") as f:
            json.dump(builder(), f, separators=(",", ":"))
        texture(os.path.join(BLOCKS, name + "_Texture.png"))
    beep(os.path.join(SOUNDS, "boot_beep.ogg"))
    print("assets generados en", BLOCKS, "y", SOUNDS)


if __name__ == "__main__":
    main()
