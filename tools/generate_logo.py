#!/usr/bin/env python3
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SCALE = 2
SIZE = 1024
OUT = Path(__file__).resolve().parent.parent / "docs" / "logo.png"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"


def sc(value):
    return int(round(value * SCALE))


def points(values):
    return [(sc(x), sc(y)) for x, y in values]


def polygon(draw, values, fill, outline=None, width=1):
    draw.polygon(points(values), fill=fill)
    if outline:
        draw.line(points(values + [values[0]]), fill=outline, width=sc(width), joint="curve")


def line(draw, values, fill, width=1):
    draw.line(points(values), fill=fill, width=sc(width), joint="curve")


def rounded(draw, box, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(tuple(sc(v) for v in box), radius=sc(radius), fill=fill,
                           outline=outline, width=sc(width))


def font(path, size):
    return ImageFont.truetype(path, sc(size))


def centered_text(draw, y, text, face, fill):
    box = draw.textbbox((0, 0), text, font=face)
    x = (sc(SIZE) - (box[2] - box[0])) // 2
    draw.text((x, sc(y)), text, font=face, fill=fill, stroke_width=0)


def main():
    s = sc(SIZE)
    yy, xx = np.mgrid[0:s, 0:s].astype(np.float32)
    nx = xx / s
    ny = yy / s
    glow = np.exp(-(((nx - 0.50) / 0.48) ** 2 + ((ny - 0.40) / 0.42) ** 2) * 2.2)
    edge = np.maximum(np.abs(nx - 0.5), np.abs(ny - 0.5))
    rng = np.random.default_rng(27)
    noise = rng.normal(0, 0.7, (s, s)).astype(np.float32)
    base = np.empty((s, s, 3), dtype=np.float32)
    for channel, (dark, bright) in enumerate(((7, 20), (15, 42), (25, 55))):
        base[:, :, channel] = dark + (bright - dark) * glow - edge * 8 + noise
    image = Image.fromarray(np.uint8(np.clip(base, 0, 255)), "RGB").convert("RGBA")

    circuits = Image.new("RGBA", image.size)
    d = ImageDraw.Draw(circuits)
    trace = (53, 181, 165, 65)
    node = (89, 222, 190, 110)
    paths = [
        [(76, 320), (150, 320), (185, 355), (185, 430)],
        [(62, 530), (126, 530), (162, 494)],
        [(90, 690), (145, 690), (185, 650)],
        [(948, 300), (874, 300), (836, 338), (836, 410)],
        [(956, 548), (895, 548), (850, 503)],
        [(934, 704), (882, 704), (842, 664)],
        [(320, 84), (320, 125), (360, 165)],
        [(706, 80), (706, 126), (672, 160)],
    ]
    for path in paths:
        line(d, path, trace, 3)
        for x, y in (path[0], path[-1]):
            d.ellipse((sc(x - 5), sc(y - 5), sc(x + 5), sc(y + 5)), fill=node)
    image = Image.alpha_composite(image, circuits)

    glow_layer = Image.new("RGBA", image.size)
    gd = ImageDraw.Draw(glow_layer)
    rounded(gd, (232, 240, 716, 644), 40, fill=(29, 239, 181, 80))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(sc(48)))
    image = Image.alpha_composite(image, glow_layer)

    shadow = Image.new("RGBA", image.size)
    sd = ImageDraw.Draw(shadow)
    polygon(sd, [(183, 704), (710, 651), (866, 752), (349, 838)], (0, 0, 0, 155))
    shadow = shadow.filter(ImageFilter.GaussianBlur(sc(24)))
    image = Image.alpha_composite(image, shadow)

    d = ImageDraw.Draw(image)

    # Turtle treads and brass hubs.
    for box in ((263, 741, 413, 824), (641, 741, 791, 824)):
        rounded(d, box, 27, fill="#111922", outline="#48515a", width=5)
        x0, y0, x1, y1 = box
        for x in range(x0 + 20, x1 - 5, 25):
            line(d, [(x, y0 + 8), (x - 5, y1 - 8)], "#303a43", 6)
        cx = (x0 + x1) // 2
        cy = (y0 + y1) // 2
        d.ellipse((sc(cx - 26), sc(cy - 26), sc(cx + 26), sc(cy + 26)),
                  fill="#6f5434", outline="#c39a55", width=sc(5))
        d.ellipse((sc(cx - 10), sc(cy - 10), sc(cx + 10), sc(cy + 10)), fill="#18242b")

    # Keyboard chassis, with depth receding up-right like the monitor.
    polygon(d, [(180, 700), (680, 700), (812, 620), (312, 620)], "#4c3d31", "#b38a4c", 5)
    polygon(d, [(180, 700), (680, 700), (680, 758), (180, 758)], "#241f20", "#725537", 4)
    polygon(d, [(680, 700), (812, 620), (812, 678), (680, 758)], "#302824", "#725537", 4)
    polygon(d, [(230, 684), (650, 684), (745, 632), (325, 632)], "#131f28", "#6e5b40", 3)

    # Individual keyboard keys, projected along the top face.
    for row in range(3):
        for col in range(10):
            x = 230 + col * 39 + row * 16
            y = 680 - row * 16
            polygon(d, [(x, y), (x + 26, y), (x + 39, y - 9), (x + 13, y - 9)],
                    "#719b89" if (row + col) % 4 else "#b69050", "#182a2c", 1)

    # Monitor shell: side, top, then brass front.
    polygon(d, [(674, 224), (802, 157), (802, 548), (674, 625)], "#493728", "#8f6d42", 6)
    polygon(d, [(220, 224), (350, 157), (802, 157), (674, 224)], "#b38b4f", "#e0b96e", 6)
    polygon(d, [(350, 176), (764, 176), (730, 193), (317, 193)], "#d0a75f")
    rounded(d, (213, 215, 687, 631), 31, fill="#a77c43", outline="#e0b96e", width=7)
    rounded(d, (237, 239, 663, 605), 22, fill="#342b28", outline="#6f4d30", width=5)
    rounded(d, (263, 267, 637, 560), 15, fill="#071b21", outline="#d0a15a", width=5)

    # Teal glass and scanlines.
    screen = Image.new("RGBA", image.size)
    scr = ImageDraw.Draw(screen)
    rounded(scr, (277, 281, 623, 546), 10, fill="#092c32")
    for y in range(288, 542, 8):
        line(scr, [(283, y), (617, y)], (55, 157, 139, 22), 2)
    image = Image.alpha_composite(image, screen)
    d = ImageDraw.Draw(image)

    prompt_glow = Image.new("RGBA", image.size)
    pg = ImageDraw.Draw(prompt_glow)
    pg.text((sc(334), sc(322)), ">_", font=font(FONT_MONO, 143), fill=(58, 255, 188, 180))
    prompt_glow = prompt_glow.filter(ImageFilter.GaussianBlur(sc(15)))
    image = Image.alpha_composite(image, prompt_glow)
    d = ImageDraw.Draw(image)
    d.text((sc(334), sc(322)), ">_", font=font(FONT_MONO, 143), fill="#79f2bd")
    rounded(d, (345, 510, 553, 518), 4, fill="#2d8d79")

    # Small status lights and lower brass detail.
    for x, color in ((278, "#77edba"), (305, "#d7aa58"), (332, "#5b6d70")):
        d.ellipse((sc(x), sc(578), sc(x + 12), sc(590)), fill=color)
    rounded(d, (543, 576, 622, 590), 5, fill="#6c4f32")

    # Wordmark.
    centered_text(d, 858, "TALECRAFT", font(FONT_BOLD, 73), "#f1e6cb")
    centered_text(d, 944, "PROGRAMMABLE COMPUTERS FOR HYTALE", font(FONT_BOLD, 19), "#69b9ab")

    border = ImageDraw.Draw(image)
    rounded(border, (12, 12, 1012, 1012), 82, outline="#9b7948", width=8)
    rounded(border, (28, 28, 996, 996), 68, outline="#284b4d", width=3)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
