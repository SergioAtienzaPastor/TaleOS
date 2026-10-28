# Credits

TaleCraft was written from scratch. These projects inspired it or helped build it:

## Inspiration (design references, no code copied)

- **Dan200** — ComputerCraft, the original programmable-computers mod.
- **SquidDev & the CC:Tweaked contributors** — [CC: Tweaked](https://github.com/cc-tweaked/CC-Tweaked),
  studied as the architectural reference (coroutine VM, instruction limits, rom layout,
  terminal design). CC:T's license restricts its code to Minecraft mods; TaleCraft
  reimplements the ideas from zero for Hytale.
- **quantumsheep** — *Computale* (Hytale), proof the concept fits Hytale.
- **Arcane Loader** (Hytale) — reference for Lua runtimes embedded in Hytale servers.

## Technology

- **[LuaJ](https://www.luaj.org/) 3.0.1** — the Lua runtime (MIT-style LuaJ license).
- **[universal-modder](https://github.com/rehan-remade/universal-modder)** (MIT) — agent
  toolkit used for asset pipeline, testing automation and publishing checks.
- **ComfyUI** + **Stable Diffusion XL** (stabilityai) — concept art generation (local).
- Hytale plugin template by **Darkhax & Jared**.

## Assets

All models (`.blockymodel`), textures, icons and sounds in this repository are original
and generated programmatically (`tools/generate_assets.py`). The TaleCraft logo is also
original and reproducible with `tools/generate_logo.py`. Concept art was generated locally
with ComfyUI/SDXL and retouched by script.

## Built by

- **kvothe** — author.
- **Kimi Code** — autonomous implementation agent.
