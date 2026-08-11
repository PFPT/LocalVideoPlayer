#!/usr/bin/env python3
from __future__ import annotations

import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
SRC = Path(__file__).resolve().parent
DOWNLOADS = Path.home() / "Downloads"

ICONS = [
    ("01_default.png", "ic_launcher", "ic_launcher_foreground"),
    ("02.png", "ic_launcher_film_reel", "ic_launcher_film_reel_foreground"),
    ("03.png", "ic_launcher_clapper_play", "ic_launcher_clapper_play_foreground"),
    ("04.png", "ic_launcher_tv_play", "ic_launcher_tv_play_foreground"),
    ("05.png", "ic_launcher_filmstrip", "ic_launcher_filmstrip_foreground"),
    ("06.png", "ic_launcher_clapper_open", "ic_launcher_clapper_open_foreground"),
    ("07.png", "ic_launcher_cam_duo", "ic_launcher_cam_duo_foreground"),
    ("08.png", "ic_launcher_letter_play", "ic_launcher_letter_play_foreground"),
    ("09.png", "ic_launcher_clapper_green", "ic_launcher_clapper_green_foreground"),
    ("10.png", "ic_launcher_cat_cam", "ic_launcher_cat_cam_foreground"),
    ("11.png", "ic_launcher_cam_outline", "ic_launcher_cam_outline_foreground"),
    ("12.png", "ic_launcher_cam_simple", "ic_launcher_cam_simple_foreground"),
    ("13.png", "ic_launcher_cam_accent", "ic_launcher_cam_accent_foreground"),
]

DOWNLOAD_SOURCES = [
    "视频.png",
    *[f"视频 ({i}).png" for i in range(1, 13)],
]

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZE = 432
ICON_FILL = 0.72
BLACK_LUMA_THRESHOLD = 28
BG_RGB = (255, 255, 255)


def copy_sources_from_downloads() -> None:
    if len(DOWNLOAD_SOURCES) != len(ICONS):
        raise SystemExit("DOWNLOAD_SOURCES / ICONS length mismatch")
    for download_name, (local_name, _, _) in zip(DOWNLOAD_SOURCES, ICONS):
        src = DOWNLOADS / download_name
        if not src.exists():
            raise SystemExit(f"Missing source PNG: {src}")
        dest = SRC / local_name
        shutil.copy2(src, dest)
        print(f"copied {src.name} -> {dest.name}")


def is_near_black(r: int, g: int, b: int, a: int) -> bool:
    if a < 8:
        return True
    luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return luma <= BLACK_LUMA_THRESHOLD


def strip_black_and_pad(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    pixels = rgba.load()
    width, height = rgba.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if is_near_black(r, g, b, a):
                pixels[x, y] = (0, 0, 0, 0)

    alpha = rgba.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return Image.new("RGBA", (width, height), (0, 0, 0, 0))
    cropped = rgba.crop(bbox)
    side = max(cropped.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(cropped, ((side - cropped.width) // 2, (side - cropped.height) // 2), cropped)
    return canvas


def scale_rgba(image: Image.Image, size: int) -> Image.Image:
    if image.size == (size, size):
        return image
    return image.resize((size, size), Image.Resampling.LANCZOS)


def place_on_canvas(icon: Image.Image, canvas_size: int, fill: float, bg: tuple[int, int, int] | None) -> Image.Image:
    target = max(1, int(canvas_size * fill))
    scaled = scale_rgba(icon, target)
    if bg is None:
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    else:
        canvas = Image.new("RGBA", (canvas_size, canvas_size), (*bg, 255))
    x = (canvas_size - scaled.width) // 2
    y = (canvas_size - scaled.height) // 2
    canvas.alpha_composite(scaled, (x, y))
    return canvas


def to_monochrome(icon: Image.Image, canvas_size: int, fill: float) -> Image.Image:
    target = max(1, int(canvas_size * fill))
    scaled = scale_rgba(icon, target)
    mono = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    alpha = scaled.getchannel("A")
    black = Image.new("RGBA", scaled.size, (0, 0, 0, 255))
    black.putalpha(alpha)
    x = (canvas_size - scaled.width) // 2
    y = (canvas_size - scaled.height) // 2
    mono.alpha_composite(black, (x, y))
    return mono


def to_adaptive(fg_name: str, mono_name: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        f'    <foreground android:drawable="@drawable/{fg_name}" />\n'
        f'    <monochrome android:drawable="@drawable/{mono_name}" />\n'
        "</adaptive-icon>\n"
    )


def cleanup_vector_artifacts(fg_names: list[str]) -> None:
    drawable = RES / "drawable"
    if not drawable.exists():
        return
    for fg_name in fg_names:
        for path in [
            drawable / f"{fg_name}.xml",
            drawable / f"{fg_name}_inset.xml",
        ]:
            if path.exists():
                path.unlink()
                print(f"removed {path}")


def main() -> int:
    copy_sources_from_downloads()

    drawable = RES / "drawable"
    anydpi = RES / "mipmap-anydpi-v26"
    drawable.mkdir(parents=True, exist_ok=True)
    anydpi.mkdir(parents=True, exist_ok=True)

    fg_names = [fg for _, _, fg in ICONS]
    cleanup_vector_artifacts(fg_names)

    for png_name, mipmap_name, fg_name in ICONS:
        src_path = SRC / png_name
        prepared = strip_black_and_pad(Image.open(src_path))
        mono_name = fg_name.replace("_foreground", "_monochrome")
        if mono_name == fg_name:
            mono_name = f"{fg_name}_monochrome"

        fg_path = drawable / f"{fg_name}.png"
        for old in drawable.glob(f"{fg_name}.*"):
            old.unlink()
        place_on_canvas(prepared, FOREGROUND_SIZE, ICON_FILL, None).save(fg_path, optimize=True)
        print(f"wrote {fg_path}")

        mono_path = drawable / f"{mono_name}.png"
        for old in drawable.glob(f"{mono_name}.*"):
            old.unlink()
        to_monochrome(prepared, FOREGROUND_SIZE, ICON_FILL).save(mono_path, optimize=True)
        print(f"wrote {mono_path}")

        adaptive_path = anydpi / f"{mipmap_name}.xml"
        adaptive_path.write_text(to_adaptive(fg_name, mono_name), encoding="utf-8")
        print(f"wrote {adaptive_path}")

        for density, size in DENSITIES.items():
            out_dir = RES / density
            out_dir.mkdir(parents=True, exist_ok=True)
            for old in out_dir.glob(f"{mipmap_name}.*"):
                old.unlink()
            out_png = out_dir / f"{mipmap_name}.png"
            place_on_canvas(prepared, size, ICON_FILL, BG_RGB).convert("RGB").save(out_png, optimize=True)
            print(f"wrote {out_png}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
