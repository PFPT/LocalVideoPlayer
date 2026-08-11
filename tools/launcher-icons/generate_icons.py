#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
SRC = Path(__file__).resolve().parent

ICONS = [
    ("01_default.svg", "ic_launcher", "ic_launcher_foreground"),
    ("02.svg", "ic_launcher_film_reel", "ic_launcher_film_reel_foreground"),
    ("03.svg", "ic_launcher_clapper_play", "ic_launcher_clapper_play_foreground"),
    ("04.svg", "ic_launcher_tv_play", "ic_launcher_tv_play_foreground"),
    ("05.svg", "ic_launcher_filmstrip", "ic_launcher_filmstrip_foreground"),
    ("06.svg", "ic_launcher_clapper_open", "ic_launcher_clapper_open_foreground"),
    ("07.svg", "ic_launcher_cam_duo", "ic_launcher_cam_duo_foreground"),
    ("08.svg", "ic_launcher_letter_play", "ic_launcher_letter_play_foreground"),
    ("09.svg", "ic_launcher_clapper_green", "ic_launcher_clapper_green_foreground"),
    ("10.svg", "ic_launcher_cat_cam", "ic_launcher_cat_cam_foreground"),
    ("11.svg", "ic_launcher_cam_outline", "ic_launcher_cam_outline_foreground"),
    ("12.svg", "ic_launcher_cam_simple", "ic_launcher_cam_simple_foreground"),
    ("13.svg", "ic_launcher_cam_accent", "ic_launcher_cam_accent_foreground"),
]

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

OLD_MIPMAP_NAMES = [
    "ic_launcher_play_square",
    "ic_launcher_film_play",
    "ic_launcher_camera_yellow",
    "ic_launcher_folder_play",
    "ic_launcher_monitor_play",
]

OLD_FOREGROUNDS = [
    "ic_launcher_play_square_foreground",
    "ic_launcher_film_play_foreground",
    "ic_launcher_camera_yellow_foreground",
    "ic_launcher_folder_play_foreground",
    "ic_launcher_monitor_play_foreground",
]


def parse_paths(svg_text: str):
    paths = []
    for match in re.finditer(
        r'<path\b([^>]*)>',
        svg_text,
        flags=re.IGNORECASE,
    ):
        attrs = match.group(1)
        d_match = re.search(r'\bd="([^"]*)"', attrs)
        if not d_match:
            continue
        fill_match = re.search(r'\bfill="([^"]*)"', attrs)
        fill = (fill_match.group(1) if fill_match else "").strip()
        if not fill or fill.lower() == "none":
            if fill.lower() == "none":
                continue
            fill = "#000000"
        if not re.fullmatch(r"#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})", fill):
            fill = "#000000"
        paths.append((d_match.group(1), fill))
    return paths


def to_vector_drawable(paths, name: str) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="1024"',
        '    android:viewportHeight="1024">',
    ]
    for d, fill in paths:
        lines.append(
            f'    <path\n'
            f'        android:fillColor="{escape(fill)}"\n'
            f'        android:pathData="{escape(d)}" />'
        )
    lines.append("</vector>")
    lines.append("")
    return "\n".join(lines)


def to_inset_drawable(fg_name: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<inset xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:drawable="@drawable/{fg_name}"\n'
        '    android:insetLeft="18%"\n'
        '    android:insetTop="18%"\n'
        '    android:insetRight="18%"\n'
        '    android:insetBottom="18%" />\n'
    )


def to_adaptive(inset_name: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        f'    <foreground android:drawable="@drawable/{inset_name}" />\n'
        "</adaptive-icon>\n"
    )


def find_rsvg() -> str | None:
    for candidate in ("rsvg-convert", "/opt/homebrew/bin/rsvg-convert", "/usr/local/bin/rsvg-convert"):
        path = shutil.which(candidate) if "/" not in candidate else candidate
        if path and Path(path).exists():
            return path
    return None


def rasterize(svg_path: Path, out_png: Path, size: int, rsvg: str) -> None:
    padded = size
    # Render icon into safe zone ~66% then pad on black
    icon_size = max(1, int(size * 0.66))
    tmp = out_png.with_suffix(".tmp.png")
    subprocess.run(
        [rsvg, "-w", str(icon_size), "-h", str(icon_size), "-o", str(tmp), str(svg_path)],
        check=True,
    )
    try:
        from PIL import Image

        icon = Image.open(tmp).convert("RGBA")
        canvas = Image.new("RGBA", (padded, padded), (0, 0, 0, 255))
        x = (padded - icon.width) // 2
        y = (padded - icon.height) // 2
        canvas.alpha_composite(icon, (x, y))
        canvas.convert("RGB").save(out_png)
    finally:
        if tmp.exists():
            tmp.unlink()


def cleanup_old() -> None:
    for density in DENSITIES:
        folder = RES / density
        if not folder.exists():
            continue
        for name in OLD_MIPMAP_NAMES:
            for path in folder.glob(f"{name}.*"):
                path.unlink()
                print(f"removed {path}")
    for folder_name in ("drawable", "drawable-nodpi"):
        folder = RES / folder_name
        if not folder.exists():
            continue
        for name in OLD_FOREGROUNDS + ["ic_launcher_foreground"]:
            for path in folder.glob(f"{name}.*"):
                if path.suffix.lower() in {".png", ".webp"}:
                    path.unlink()
                    print(f"removed {path}")
    anydpi = RES / "mipmap-anydpi-v26"
    for name in OLD_MIPMAP_NAMES:
        path = anydpi / f"{name}.xml"
        if path.exists():
            path.unlink()
            print(f"removed {path}")


def main() -> int:
    rsvg = find_rsvg()
    if not rsvg:
        print("WARNING: rsvg-convert not found; skipping mipmap PNG generation", file=sys.stderr)

    drawable = RES / "drawable"
    anydpi = RES / "mipmap-anydpi-v26"
    drawable.mkdir(parents=True, exist_ok=True)
    anydpi.mkdir(parents=True, exist_ok=True)

    cleanup_old()

    for svg_name, mipmap_name, fg_name in ICONS:
        svg_path = SRC / svg_name
        svg_text = svg_path.read_text(encoding="utf-8")
        paths = parse_paths(svg_text)
        if not paths:
            raise SystemExit(f"No paths in {svg_path}")

        fg_path = drawable / f"{fg_name}.xml"
        fg_path.write_text(to_vector_drawable(paths, fg_name), encoding="utf-8")
        print(f"wrote {fg_path}")

        inset_name = f"{fg_name}_inset"
        inset_path = drawable / f"{inset_name}.xml"
        inset_path.write_text(to_inset_drawable(fg_name), encoding="utf-8")
        print(f"wrote {inset_path}")

        adaptive_path = anydpi / f"{mipmap_name}.xml"
        adaptive_path.write_text(to_adaptive(inset_name), encoding="utf-8")
        print(f"wrote {adaptive_path}")

        if rsvg:
            for density, size in DENSITIES.items():
                out_dir = RES / density
                out_dir.mkdir(parents=True, exist_ok=True)
                # Remove old png for this mipmap name if regenerating
                for old in out_dir.glob(f"{mipmap_name}.*"):
                    old.unlink()
                out_png = out_dir / f"{mipmap_name}.png"
                rasterize(svg_path, out_png, size, rsvg)
                print(f"wrote {out_png}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
