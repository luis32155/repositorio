from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


preview_dir = Path(__file__).resolve().parent / "previews"
names = [
    "00-inicio.png",
    "01-cbs-listar-cuentas.png",
    "02-cbs-validar-tarjeta.png",
    "03-cbs-consultar-saldo.png",
    "04-cbs-credito-reversa.png",
    "05-cbs-debito.png",
    "06-cbs-estado-trans.png",
    "07-int-listar-cuentas.png",
    "08-int-detalle-cuenta.png",
    "09-int-saldo-cuenta.png",
    "10-int-perfil-cliente.png",
    "11-int-validar-tarjeta.png",
    "12-int-crear-credito.png",
    "13-int-crear-debito.png",
    "14-int-reversar-debito.png",
    "90-catalogos.png",
    "91-asyncapi.png",
    "92-eventos-async.png",
    "93-avro.png",
    "94-pendientes-peru.png",
    "95-consultas-niubiz.png",
    "96-fuentes.png",
]

thumb_width = 720
thumb_height = 360
label_height = 34
margin = 16
columns = 2
rows = 2
font = ImageFont.load_default(size=18)

for page, start in enumerate(range(0, len(names), columns * rows), start=1):
    batch = names[start : start + columns * rows]
    canvas = Image.new(
        "RGB",
        (
            margin + columns * (thumb_width + margin),
            margin + rows * (thumb_height + label_height + margin),
        ),
        "white",
    )
    draw = ImageDraw.Draw(canvas)
    for index, name in enumerate(batch):
        image = Image.open(preview_dir / name).convert("RGB")
        image.thumbnail((thumb_width, thumb_height), Image.Resampling.LANCZOS)
        row = index // columns
        col = index % columns
        x = margin + col * (thumb_width + margin)
        y = margin + row * (thumb_height + label_height + margin)
        draw.text((x, y), name.removesuffix(".png"), fill="#1F3A24", font=font)
        image_y = y + label_height
        canvas.paste(image, (x, image_y))
        draw.rectangle(
            [x, image_y, x + image.width, image_y + image.height],
            outline="#A7CDB1",
            width=2,
        )
    canvas.save(preview_dir / f"ordered-contact-{page}.png")

