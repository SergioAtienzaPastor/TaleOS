#!/usr/bin/env python3
import os

from generate_assets import BLOCKS, RES, box, box_mapped, model, render_icon, texture


def speaker_model():
    wooden = {face: "wood" for face in ("front", "back", "right", "bottom", "left", "top")}
    return model([
        box(10, "Base", (30, 2, 24), (0, 1, 0), "brass"),
        box_mapped(11, "Cabinet", (28, 29, 22), (0, 16.5, -1), wooden),
        box(12, "TopTrim", (30, 2, 24), (0, 31, -1), "brass"),
        box(13, "FrontPanel", (24, 25, 1), (0, 16.5, 10.25), "keys"),
        box(14, "WooferFrame", (20, 16, 1), (0, 13, 11), "brass"),
        box(15, "WooferSurround", (16, 12, 1), (0, 13, 11.75), "screen"),
        box(16, "WooferCone", (12, 8, 1), (0, 13, 12.25), "keys"),
        box(17, "DustCap", (6, 4, 2), (0, 13, 12.75), "screen"),
        box(18, "TweeterFrame", (8, 6, 1), (0, 26, 11), "brass"),
        box(19, "TweeterCone", (4, 4, 1), (0, 26, 11.75), "screen"),
    ])


def speaker_texture(path):
    texture(path, prompt_glyph=False)


def main():
    os.makedirs(BLOCKS, exist_ok=True)
    icons = os.path.join(RES, "Common", "Icons", "ItemsGenerated")
    os.makedirs(icons, exist_ok=True)
    model_path = os.path.join(BLOCKS, "TaleCraft_Speaker.blockymodel")
    texture_path = os.path.join(BLOCKS, "TaleCraft_Speaker_Texture.png")
    import json
    with open(model_path, "w") as output:
        json.dump(speaker_model(), output, separators=(",", ":"))
    speaker_texture(texture_path)
    render_icon(model_path, texture_path, os.path.join(icons, "TaleCraft_Speaker.png"))


if __name__ == "__main__":
    main()
