#!/usr/bin/env python3
"""Genera las imagenes de demo del catalogo de FlashDrop.

El catalogo guarda en la columna `image` la **clave del objeto** en el
bucket privado (`products/<anio>/<mes>/<uuid>.<ext>`), y el servicio la
sirve por `/catalog/images/**`. Estas imagenes son los assets de la demo:
ilustraciones planas, una por plato, que se suben al bucket con
`upload.sh` y que la migracion `V4__set_demo_product_images.sql` deja
apuntadas en el catalogo.

No son fotos: son marcadores de posicion. Para reemplazarlas por fotos
reales hay que subir el archivo por la API del dueno
(`POST /api/catalog/my/products/image`), que devuelve la clave del objeto
y la deja lista para el `PUT` del producto.

Uso:  python3 infra/demo-images/generate.py
"""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 800, 600

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE / "products" / "2026" / "11"

# Paleta de la marca (mismo naranja que el shell del front).
BRAND = (232, 78, 14)
INK = (60, 42, 33)
INK_MUTED = (122, 102, 90)

BUN = (232, 183, 107)
BUN_DARK = (214, 154, 78)
PATTY = (90, 55, 37)
LETTUCE = (127, 184, 0)
CHEESE = (246, 196, 83)
CRUST = (219, 165, 95)
PEPPERONI = (192, 57, 43)
CUP = (228, 241, 249)
LIQUID = (129, 194, 222)
RED = (214, 69, 65)


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    """Primera fuente disponible del sistema."""
    names = (
        ["arialbd.ttf", "segoeuib.ttf", "DejaVuSans-Bold.ttf"]
        if bold
        else ["arial.ttf", "segoeui.ttf", "DejaVuSans.ttf"]
    )

    for name in names:
        for folder in ("C:/Windows/Fonts", "/usr/share/fonts/truetype/dejavu", "/Library/Fonts"):
            candidate = Path(folder) / name
            if candidate.exists():
                return ImageFont.truetype(str(candidate), size)

    raise RuntimeError("no se encontro ninguna fuente del sistema")


def gradient(top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    """Fondo vertical de dos tonos."""
    image = Image.new("RGB", (WIDTH, HEIGHT), top)
    draw = ImageDraw.Draw(image)

    for y in range(HEIGHT):
        ratio = y / (HEIGHT - 1)
        draw.line(
            [(0, y), (WIDTH, y)],
            fill=tuple(round(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3)),
        )

    return image


def draw_burger(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    draw.pieslice([x0 + w * 0.10, y0, x0 + w * 0.90, y0 + h * 0.84], 180, 360, fill=BUN)
    draw.rounded_rectangle(
        [x0 + w * 0.06, y0 + h * 0.40, x0 + w * 0.94, y0 + h * 0.52], radius=int(h * 0.06), fill=LETTUCE
    )
    draw.rounded_rectangle(
        [x0 + w * 0.09, y0 + h * 0.50, x0 + w * 0.91, y0 + h * 0.68], radius=int(h * 0.05), fill=PATTY
    )
    draw.rounded_rectangle(
        [x0 + w * 0.12, y0 + h * 0.66, x0 + w * 0.88, y0 + h * 0.94], radius=int(h * 0.08), fill=BUN_DARK
    )

    for offset in (0.28, 0.46, 0.64):
        draw.ellipse(
            [
                x0 + w * offset,
                y0 + h * 0.12,
                x0 + w * offset + w * 0.07,
                y0 + h * 0.12 + w * 0.045,
            ],
            fill=(250, 238, 214),
        )


def draw_pizza(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    apex = (x0 + w * 0.50, y0)
    left = (x0 + w * 0.04, y0 + h)
    right = (x0 + w * 0.96, y0 + h)

    draw.polygon([apex, left, right], fill=CRUST)

    inner_apex = (x0 + w * 0.50, y0 + h * 0.16)
    inner_left = (x0 + w * 0.17, y0 + h * 0.88)
    inner_right = (x0 + w * 0.83, y0 + h * 0.88)
    draw.polygon([inner_apex, inner_left, inner_right], fill=CHEESE)

    for cx, cy, r in ((0.38, 0.62, 0.07), (0.62, 0.62, 0.07), (0.50, 0.44, 0.06), (0.30, 0.78, 0.05), (0.70, 0.78, 0.05)):
        draw.ellipse(
            [x0 + w * (cx - r), y0 + h * (cy - r * 0.8), x0 + w * (cx + r), y0 + h * (cy + r * 0.8)],
            fill=PEPPERONI,
        )


def draw_drink(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    draw.polygon(
        [
            (x0 + w * 0.52, y0),
            (x0 + w * 0.62, y0 + h * 0.22),
            (x0 + w * 0.70, y0 + h * 0.22),
            (x0 + w * 0.60, y0),
        ],
        fill=BRAND,
    )
    draw.rounded_rectangle(
        [x0 + w * 0.28, y0 + h * 0.20, x0 + w * 0.72, y0 + h * 0.30], radius=int(h * 0.04), fill=(236, 236, 236)
    )
    draw.polygon(
        [
            (x0 + w * 0.32, y0 + h * 0.28),
            (x0 + w * 0.68, y0 + h * 0.28),
            (x0 + w * 0.76, y0 + h),
            (x0 + w * 0.24, y0 + h),
        ],
        fill=CUP,
    )
    draw.polygon(
        [
            (x0 + w * 0.35, y0 + h * 0.58),
            (x0 + w * 0.65, y0 + h * 0.58),
            (x0 + w * 0.73, y0 + h * 0.96),
            (x0 + w * 0.27, y0 + h * 0.96),
        ],
        fill=LIQUID,
    )


def draw_fries(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    for lean, height in ((0.34, 0.52), (0.42, 0.60), (0.50, 0.64), (0.58, 0.60), (0.66, 0.52)):
        draw.polygon(
            [
                (x0 + w * (lean - 0.045), y0 + h * 0.55),
                (x0 + w * (lean + 0.045), y0 + h * 0.55),
                (x0 + w * (lean + 0.065), y0 + h * (0.55 - height)),
                (x0 + w * (lean - 0.065), y0 + h * (0.55 - height)),
            ],
            fill=CHEESE,
        )

    draw.polygon(
        [
            (x0 + w * 0.24, y0 + h * 0.52),
            (x0 + w * 0.76, y0 + h * 0.52),
            (x0 + w * 0.88, y0 + h),
            (x0 + w * 0.12, y0 + h),
        ],
        fill=RED,
    )
    draw.rectangle([x0 + w * 0.12, y0 + h * 0.70, x0 + w * 0.88, y0 + h * 0.80], fill=(250, 250, 250))


def draw_combo(draw: ImageDraw.ImageDraw, box: tuple[float, float, float, float]) -> None:
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    draw_burger(draw, (x0, y0 + h * 0.22, x0 + w * 0.60, y0 + h))
    draw_drink(draw, (x0 + w * 0.58, y0, x0 + w, y0 + h * 0.95))


DISHES = (
    {
        "slug": "burger-doble",
        "uuid": "fda64c8e-8b5c-41c9-b3f4-73cb88bfa83f",
        "name": "Burger doble",
        "restaurant": "Urban Burger Demo",
        "art": draw_burger,
        "top": (253, 241, 231),
        "bottom": (245, 221, 200),
    },
    {
        "slug": "papas-cheddar",
        "uuid": "5f295eae-97dd-4acc-bbea-1a2993fb250d",
        "name": "Papas cheddar",
        "restaurant": "Urban Burger Demo",
        "art": draw_fries,
        "top": (255, 247, 232),
        "bottom": (249, 231, 198),
    },
    {
        "slug": "pizza-maipu",
        "uuid": "5793b271-6922-4c87-ac48-d997bacf54b9",
        "name": "Pizza Maipú familiar",
        "restaurant": "Flash Bites Arauco Maipú",
        "art": draw_pizza,
        "top": (255, 246, 230),
        "bottom": (249, 226, 192),
    },
    {
        "slug": "bebida-500cc",
        "uuid": "91c85abb-79fb-4809-9625-62f3cf474663",
        "name": "Bebida 500 cc",
        "restaurant": "Flash Bites Arauco Maipú",
        "art": draw_drink,
        "top": (236, 246, 252),
        "bottom": (203, 229, 244),
    },
    {
        "slug": "combo-flash",
        "uuid": "040bfd80-c114-42a9-9c94-8df47f5f1b0a",
        "name": "Combo Flash",
        "restaurant": "Flash Restaurant Demo",
        "art": draw_combo,
        "top": (255, 241, 233),
        "bottom": (247, 216, 201),
    },
    {
        "slug": "burger-maipu",
        "uuid": "d59c887a-ca1f-48b2-92f8-8c70f956b258",
        "name": "Burger Maipú",
        "restaurant": "Flash Bites Arauco Maipú",
        "art": draw_burger,
        "top": (246, 245, 237),
        "bottom": (226, 224, 209),
    },
)


def render(dish: dict) -> Image.Image:
    image = gradient(dish["top"], dish["bottom"])
    draw = ImageDraw.Draw(image)

    art_box = (170, 70, 630, 400)
    dish["art"](draw, art_box)

    name_font = load_font(46, bold=True)
    restaurant_font = load_font(26)

    name = dish["name"]
    restaurant = dish["restaurant"]
    draw.text((WIDTH / 2, 448), name, font=name_font, fill=INK, anchor="mm")
    draw.text((WIDTH / 2, 508), restaurant, font=restaurant_font, fill=INK_MUTED, anchor="mm")

    return image


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for dish in DISHES:
        target = OUT_DIR / f"{dish['uuid']}.png"
        render(dish).save(target, format="PNG", optimize=True)
        print(f"{target.relative_to(HERE)}  ({os.path.getsize(target) // 1024} KB)")


if __name__ == "__main__":
    main()
