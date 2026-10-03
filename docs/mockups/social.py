#!/usr/bin/env python3
"""Builds the social-media images in docs/social/ from docs/screens/*.png (run render.sh first). Needs Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

S, OUT = Path(__file__).resolve().parent.parent / "screens", Path(__file__).resolve().parent.parent / "social"
OUT.mkdir(exist_ok=True)
BG, ACC, ON, VAR = (14, 14, 16), (217, 119, 87), (241, 239, 233), (185, 182, 173)


def font(size, bold=False):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if bold else ""), size)


def gradient(w, h):
    img = Image.new("RGB", (w, h), BG)
    glow = Image.new("RGB", (w, h), (0, 0, 0))
    ImageDraw.Draw(glow).ellipse((w * .35, -h * .5, w * 1.2, h * .6), fill=(90, 44, 28))
    glow = glow.filter(ImageFilter.GaussianBlur(h // 4))
    return Image.blend(img, Image.composite(glow, img, glow.convert("L")), .9)


def phone(name, width, label=None):
    """A screen with rounded corners and a bezel, ready to paste."""
    im = Image.open(S / f"{name}.png").convert("RGB")
    im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    r, b = width // 11, max(5, width // 55)
    out = Image.new("RGBA", (im.width + 2 * b, im.height + 2 * b), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((0, 0, out.width - 1, out.height - 1), r + b, fill=(38, 38, 42))
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out.paste(im, (b, b), mask)
    return out


def shadow_paste(canvas, img, xy):
    pad = 60   # the blur needs room, or the shadow gets a hard edge
    sh = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    sh.paste((0, 0, 0, 160), (pad, pad), mask=img.split()[3])
    sh = sh.filter(ImageFilter.GaussianBlur(20))
    canvas.paste(sh, (xy[0] - pad + 4, xy[1] - pad + 14), sh)
    canvas.paste(img, xy, img)


def text(d, xy, s, size, fill=ON, bold=False, anchor="la"):
    d.text(xy, s, font=font(size, bold), fill=fill, anchor=anchor)


# 1. Wide hero 1200x630 (Facebook link / cover)
W, H = 1200, 630
im = gradient(W, H).convert("RGBA")
d = ImageDraw.Draw(im)
text(d, (70, 120), "cLaudeRC", 84, ON, True)
text(d, (72, 228), "Run Claude Code", 36, ACC, True)
text(d, (72, 274), "from your phone", 36, ACC, True)
for i, line in enumerate(["Chat with every session, answer questions,", "see usage across accounts, get instant alerts.", "Your server. Your key. No cloud service."]):
    text(d, (72, 345 + i * 34), line, 21, VAR)
text(d, (72, 520), "Android  ·  iPhone soon  ·  open source", 22, ON, True)
for i, (n, x, y) in enumerate([("sessions", 610, 100), ("chat", 790, 40), ("accounts", 970, 100)]):
    shadow_paste(im, phone(n, 190), (x, y))
im.convert("RGB").save(OUT / "hero-1200x630.png")

# 2. Square feature grid 1080x1080
W = H = 1080
im = gradient(W, H).convert("RGBA")
d = ImageDraw.Draw(im)
text(d, (W // 2, 90), "cLaudeRC", 60, ON, True, "ma")
text(d, (W // 2, 168), "Everything about your Claude Code server, in your pocket", 24, VAR, False, "ma")
cells = [("sessions", "Sessions & chat"), ("accounts", "cLaudeCluster"), ("push", "Instant alerts"), ("conn-logins", "Connections")]
for i, (n, label) in enumerate(cells):
    ph = phone(n, 240)
    x = 42 + i * 258
    shadow_paste(im, ph, (x, 285))
    text(d, (x + ph.width // 2, 285 + ph.height + 30), label, 23, ON, True, "ma")
text(d, (W // 2, 1000), "Free · open source · Android 8+ · iPhone coming soon", 22, ACC, True, "ma")
im.convert("RGB").save(OUT / "features-1080x1080.png")

# 3. Android + iPhone 1080x1080
im = gradient(W, H).convert("RGBA")
d = ImageDraw.Draw(im)
text(d, (W // 2, 70), "Coming to iPhone", 60, ON, True, "ma")
text(d, (W // 2, 150), "The same cLaudeRC: sessions, chat, projects, over your own SSH key", 23, VAR, False, "ma")
a, b = phone("sessions", 380), phone("ios-sessions", 380)
shadow_paste(im, a, (100, 215))
shadow_paste(im, b, (590, 215))
text(d, (100 + a.width // 2, 215 + a.height + 36), "Android", 28, ON, True, "ma")
text(d, (590 + b.width // 2, 215 + b.height + 36), "iPhone", 28, ON, True, "ma")
im.convert("RGB").save(OUT / "android-iphone-1080x1080.png")

# 4. One tall post 1080x1350 for the new cluster settings + hand-back
W, H = 1080, 1350
im = gradient(W, H).convert("RGBA")
d = ImageDraw.Draw(im)
text(d, (W // 2, 70), "Your Claude accounts, working as a team", 40, ON, True, "ma")
text(d, (W // 2, 128), "Main Claude plans and reviews. Workers write the code.", 24, VAR, False, "ma")
a, b = phone("accounts", 450), phone("chat-handback", 450)
shadow_paste(im, a, (58, 215))
shadow_paste(im, b, (572, 215))
text(d, (58 + a.width // 2, 215 + a.height + 36), "Usage + settings per account", 24, ON, True, "ma")
text(d, (572 + b.width // 2, 215 + b.height + 36), "Hands work back near the limit", 24, ON, True, "ma")
im.convert("RGB").save(OUT / "cluster-1080x1350.png")
print("ok", sorted(p.name for p in OUT.iterdir()))
