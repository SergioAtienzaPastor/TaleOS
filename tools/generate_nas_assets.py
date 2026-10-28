#!/usr/bin/env python3
import json
import os

from generate_assets import BLOCKS, FACES, RES, box, box_mapped, model, render_icon
from generate_usb_assets import texture


def nas_model():
    body = {face: "keys" for face in FACES}
    body["top"] = "brass"
    parts = [
        box(10, "Base", (32, 2, 28), (0, 1, 0), "wood"),
        box_mapped(11, "Rack", (30, 29, 24), (0, 16.5, -2), body),
        box(12, "TopTrim", (32, 2, 26), (0, 31, -1), "brass"),
    ]
    for index, x in enumerate((-10.5, -3.5, 3.5, 10.5)):
        for row, y in enumerate((10, 21)):
            slot = row * 4 + index
            parts.append(box(20 + slot, "Bay" + str(slot + 1), (6, 6, 2),
                             (x, y, 11), "screen"))
            parts.append(box(30 + slot, "BayLight" + str(slot + 1), (2, 1, 1),
                             (x + 1.5, y - 2, 12.5), "wood"))
    return model(parts)


def main():
    os.makedirs(BLOCKS, exist_ok=True)
    icons = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
    os.makedirs(icons, exist_ok=True)
    model_path = os.path.join(BLOCKS, "TaleCraft_NAS.blockymodel")
    texture_path = os.path.join(BLOCKS, "TaleCraft_NAS_Texture.png")
    with open(model_path, "w") as output:
        json.dump(nas_model(), output, separators=(",", ":"))
    texture(texture_path)
    render_icon(model_path, texture_path, os.path.join(icons, "TaleCraft_NAS.png"))
    print("generado TaleCraft_NAS")


if __name__ == "__main__":
    main()
