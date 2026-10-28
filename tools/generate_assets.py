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


def face_layout_mapped(mapping):
    """Per-face cells: mapping face name -> cell name."""
    out = {}
    for f in FACES:
        u, v = CELL[mapping[f]]
        out[f] = {"offset": {"x": u, "y": v}, "mirror": {"x": False, "y": False}, "angle": 0}
    return out


def box_mapped(nid, name, size, center, mapping):
    b = box(nid, name, size, center, "brass")
    b["shape"]["textureLayout"] = face_layout_mapped(mapping)
    return b


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
    # Panel plano estilo CC:T: un bloque completo con la pantalla en la cara
    # frontal, para que varios monitores juntos formen una pared continua.
    faces = {f: "brass" for f in FACES}
    faces["front"] = "screen"
    return model([
        box_mapped(10, "Panel", (32, 32, 32), (0, 16, 0), faces),
    ])


def modem_model():
    faces = {f: "brass" for f in FACES}
    faces["front"] = "screen"
    return model([
        box(10, "Base", (26, 5, 26), (0, 2.5, 0), "wood"),
        box_mapped(11, "Radio", (22, 10, 20), (0, 10, 0), faces),
        box(12, "AntennaFoot", (8, 3, 8), (0, 16.5, -3), "keys"),
        box(13, "Antenna", (2, 14, 2), (0, 25, -3), "brass"),
        box(14, "AntennaTip", (5, 4, 5), (0, 32, -3), "screen"),
    ])


def turtle_model():
    return model([
        box(10, "LeftTread", (4, 8, 28), (-13, 4, 0), "keys"),
        box(11, "RightTread", (4, 8, 28), (13, 4, 0), "keys"),
        box(12, "LeftFrontHub", (2, 5, 7), (-15, 4, 8), "brass"),
        box(13, "RightFrontHub", (2, 5, 7), (15, 4, 8), "brass"),
        box(14, "LeftRearHub", (2, 5, 7), (-15, 4, -8), "brass"),
        box(15, "RightRearHub", (2, 5, 7), (15, 4, -8), "brass"),
        box(16, "Chassis", (26, 10, 24), (0, 10, 0), "wood"),
        box(17, "Body", (24, 16, 16), (0, 20, 0), "brass"),
        box(18, "Screen", (24, 12, 2), (0, 20, 9), "screen"),
        box(19, "TopTrim", (28, 2, 18), (0, 29, 0), "brass"),
        box(20, "ControlDeck", (20, 2, 4), (0, 13, 12), "keys"),
    ])


def texture(path, prompt_glyph=True):
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

    # prompt ">_" verde fosforo en la esquina superior izquierda de la pantalla:
    # hace el bloque reconocible como terminal en el mundo y en el icono.
    # Solo en el Computer: el Monitor pinta su contenido real en la pantalla
    # (DisplayDebug) y el glifo estático molestaría.
    if prompt_glyph:
        u0, v0 = CELL["screen"]
        green = (150, 225, 160)
        for i in range(5):                      # chevron ">" de 5px de alto
            for thick in (0, 1):
                img.putpixel((u0 + 4 + min(i, 4 - i) + thick, v0 + 5 + i), green)
        for dx in range(5):                     # guion bajo "_"
            for dy in (10, 11):
                img.putpixel((u0 + 9 + dx, v0 + dy), green)
    img.save(path)


def render_icon(model_path, texture_path, out_path, canvas=256):
    """Render isométrico del blockymodel al estilo de los iconos vanilla de
    Icons/ItemsGenerated (vista frente-arriba-derecha, fondo transparente).
    Rasteriza por caras con z-buffer y sombreado por orientación; el icono
    final se guarda en 64x64 (downscale desde `canvas` = antialiasing)."""
    data = json.load(open(model_path))
    tex = np.asarray(Image.open(texture_path).convert("RGB"), dtype=np.float32)

    boxes = []

    def walk(node, base):
        pos = node["position"]
        center = (base[0] + pos["x"], base[1] + pos["y"], base[2] + pos["z"])
        shape = node["shape"]
        if shape["type"] == "box" and node.get("visible", True):
            size = shape["settings"]["size"]
            boxes.append((center, (size["x"], size["y"], size["z"]), shape["textureLayout"]))
        for child in node.get("children", []):
            walk(child, center)

    for root in data["nodes"]:
        walk(root, (0.0, 0.0, 0.0))

    cos30 = math.sqrt(3) / 2

    def proj(p):
        x, y, z = p
        return np.array([(x - z) * cos30, (x + z) * 0.5 - y])

    def depth(p):
        return p[0] + p[1] + p[2]

    # primera pasada: encuadre del modelo en el canvas
    all_pts = []
    for center, size, _layout in boxes:
        cx, cy, cz = center
        sx, sy, sz = (s / 2 for s in size)
        for dx in (-sx, sx):
            for dy in (-sy, sy):
                for dz in (-sz, sz):
                    all_pts.append(proj((cx + dx, cy + dy, cz + dz)))
    min_px = np.min([p[0] for p in all_pts])
    max_px = np.max([p[0] for p in all_pts])
    min_py = np.min([p[1] for p in all_pts])
    max_py = np.max([p[1] for p in all_pts])
    shift = np.array([canvas / 2 - (min_px + max_px) / 2, canvas / 2 - (min_py + max_py) / 2])

    def projc(p):
        return proj(p) + shift

    # cara: (nombre, normal-light, esquinas 3D en orden, ejes uv)
    # uv: qué ejes del mundo alimentan u y v de la textura (celda 32x32)
    img = np.zeros((canvas, canvas, 4), dtype=np.float32)
    zbuf = np.full((canvas, canvas), -1e9, dtype=np.float32)

    for center, size, layout in boxes:
        cx, cy, cz = center
        sx, sy, sz = (s / 2 for s in size)
        x0, x1 = cx - sx, cx + sx
        y0, y1 = cy - sy, cy + sy
        z0, z1 = cz - sz, cz + sz
        faces = [
            # (face key, light, 4 corners, u axis, v axis, u range, v range)
            ("top", 1.00, [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], 0, 2),
            ("front", 0.80, [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], 0, 1),
            ("right", 0.62, [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], 2, 1),
        ]
        for face, light, corners, ua, va in faces:
            if face not in layout:
                continue
            off = layout[face]["offset"]
            ou, ov = off["x"], off["y"]
            pts = [projc(c) for c in corners]
            # base afín: píxel = A @ (u, v) + t ; u/v recorren la celda 32x32
            u0v0 = corners[0]
            du = np.array(corners[1]) - np.array(corners[0])  # +u mundial
            dv = np.array(corners[3]) - np.array(corners[0])  # +v mundial
            su = max(abs(corners[1][ua] - corners[0][ua]), 1e-6)
            sv = max(abs(corners[3][va] - corners[0][va]), 1e-6)
            p00 = pts[0]
            pu = (pts[1] - pts[0]) / su
            pv = (pts[3] - pts[0]) / sv
            d0 = depth(u0v0)
            du_d = (depth(corners[1]) - d0) / su
            dv_d = (depth(corners[3]) - d0) / sv
            minx = max(int(min(p[0] for p in pts)), 0)
            maxx = min(int(max(p[0] for p in pts)) + 1, canvas)
            miny = max(int(min(p[1] for p in pts)), 0)
            maxy = min(int(max(p[1] for p in pts)) + 1, canvas)
            if minx >= maxx or miny >= maxy:
                continue
            # inversa 2x2 para pasar de píxel a (u, v)
            a, b = pu[0], pv[0]
            c_, d = pu[1], pv[1]
            det = a * d - b * c_
            if abs(det) < 1e-9:
                continue
            ia, ib, ic, id_ = d / det, -b / det, -c_ / det, a / det
            ys, xs = np.mgrid[miny:maxy, minx:maxx].astype(np.float32) + 0.5
            rx = xs - p00[0]
            ry = ys - p00[1]
            uu = ia * rx + ib * ry
            vv = ic * rx + id_ * ry
            inside = (uu >= 0) & (uu < su) & (vv >= 0) & (vv < sv)
            if not inside.any():
                continue
            tu = np.clip((ou + uu * (32.0 / su) if su > 32 else ou + uu).astype(int), 0, 63)
            tv = np.clip((ov + vv * (32.0 / sv) if sv > 32 else ov + vv).astype(int), 0, 63)
            dep = d0 + uu * du_d + vv * dv_d
            wins = inside & (dep > zbuf[miny:maxy, minx:maxx])
            if not wins.any():
                continue
            shade = light * (1.0 - 0.10 * (vv / max(sv, 1e-6)))
            rgb = tex[tv, tu] * shade[..., None]
            region = zbuf[miny:maxy, minx:maxx]
            region[wins] = dep[wins]
            block = img[miny:maxy, minx:maxx]
            block[wins, :3] = rgb[wins]
            block[wins, 3] = 255.0

    # recorte al contenido, cuadrado, escala a 64x64 con margen
    alpha = img[:, :, 3] > 0
    if not alpha.any():
        raise RuntimeError("render vacío: " + model_path)
    ys, xs = np.where(alpha)
    crop = img[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = crop.shape[:2]
    side = int(max(h, w) / 0.86)
    out = np.zeros((side, side, 4), dtype=np.float32)
    oy = (side - h) // 2
    ox = (side - w) // 2
    out[oy:oy + h, ox:ox + w] = crop
    final = Image.fromarray(out.astype(np.uint8), "RGBA").resize((64, 64), Image.LANCZOS)
    final.save(out_path)


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
    icons = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
    os.makedirs(icons, exist_ok=True)
    builders = (
        ("TaleCraft_Computer", computer_model),
        ("TaleCraft_Monitor", monitor_model),
        ("TaleCraft_Modem", modem_model),
        ("TaleCraft_Turtle", turtle_model),
    )
    for name, builder in builders:
        model_file = os.path.join(BLOCKS, name + ".blockymodel")
        tex_file = os.path.join(BLOCKS, name + "_Texture.png")
        with open(model_file, "w") as f:
            json.dump(builder(), f, separators=(",", ":"))
        texture(tex_file, prompt_glyph=(name not in ("TaleCraft_Monitor", "TaleCraft_Modem")))
        render_icon(model_file, tex_file, os.path.join(icons, name + ".png"))
    beep(os.path.join(SOUNDS, "boot_beep.ogg"))
    print("assets generados en", BLOCKS, ",", SOUNDS, "e", icons)


if __name__ == "__main__":
    main()
