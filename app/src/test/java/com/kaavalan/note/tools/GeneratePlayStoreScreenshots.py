"""
v1.9.1 (PROD-READINESS-P3-P2-#3 wiring): Play Store
screenshot stub generator.

The Play Store requires 2-8 phone screenshots at 1080x1920
(9:16 aspect ratio, the documented "phone" form factor).
v1.9.0 shipped the v1.9.0 listing doc with an 8-screenshot
spec but no actual PNGs. v1.9.1 ships the spec PNGs as
placeholders so the listing is "complete" structurally —
a real submission would replace these with
drive-verify captures from a connected device or
emulator.

The placeholder images:
  - Use the actual M3 colour tokens from
    `ui/theme/Color.kt` (the cream + indigo + charcoal
    palette) so the placeholders look on-brand.
  - Carry a per-screen title + 1-line caption + a
    "PLACEHOLDER" stripe at the top so a real submission
    cannot accidentally ship these.
  - Are 1080x1920 PNG at 72 dpi (the Play Store
    recommendation; the minimum short edge is 320px,
    the maximum 3840px, so 1080px is mid-range and
    uploads cleanly).

Usage (from the repo root):
    python app/src/test/java/com/kaavalan-note/app/tools/GeneratePlayStoreScreenshots.py

The script writes 8 PNGs to
`docs/play-store-screenshots/`. The matching
`docs/play-store-listing.md` already names them
(`01-home.png` .. `08-vault.png`); this script
writes the same names.
"""

from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

WIDTH = 1080
HEIGHT = 1920

# Kaavalan brand palette — kept in sync with `ui/theme/Color.kt`.
CREAM = (250, 245, 235)        # background
INDIGO = (55, 48, 120)         # primary
CHARCOAL = (40, 35, 30)        # onSurface
AMBER = (217, 119, 6)          # warning
SURFACE = (255, 255, 255)      # cards
PLACEHOLDER_RED = (220, 38, 38)  # "PLACEHOLDER" stripe

# Spec from docs/play-store-listing.md v1.9.0.
SCREENS = [
    ("01-home", "People", "Every person in your beat at a glance, with the count of open instructions next to their name."),
    ("02-today", "Today", "The day's brief: who needs you, who you're waiting on, and what's been carried over."),
    ("03-capture", "Capture", "Type, dictate, or shoot a photo. Kaavalan ties every note to a person, not a folder."),
    ("04-decay", "Decay", "People you have not touched in 30 / 60 / 90 days. A nudge to follow up, not a guilt trip."),
    ("05-search", "Search", "Full-text search across every note. Find what you gave a person, even months later."),
    ("06-settings", "Settings", "Vault, backup, threat model, recovery phrase, support — everything in one place."),
    ("07-threat-model", "Threat model", "What Kaavalan protects against, what it does not, and what you are responsible for."),
    ("08-vault", "Vault", "Lock the app behind a 6-digit PIN. Hide sensitive people from the list with one tap."),
]


def font(size: int) -> ImageFont.FreeTypeFont:
    """Resolve a usable font. The Windows install has arial.ttf
    at the standard location; fall back to the PIL default
    (DejaVu) on systems without arial."""
    candidates = [
        r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def draw_screen(out: Path, name: str, title: str, caption: str) -> None:
    img = Image.new("RGB", (WIDTH, HEIGHT), CREAM)
    draw = ImageDraw.Draw(img)

    # Top "PLACEHOLDER" stripe — explicit marker so the
    # placeholders cannot be mistaken for the real thing.
    draw.rectangle((0, 0, WIDTH, 80), fill=PLACEHOLDER_RED)
    placeholder_font = font(36)
    placeholder_text = "PLACEHOLDER — REPLACE BEFORE PLAY STORE SUBMISSION"
    pw = draw.textlength(placeholder_text, font=placeholder_font)
    draw.text(
        ((WIDTH - pw) / 2, 22),
        placeholder_text,
        font=placeholder_font,
        fill=(255, 255, 255),
    )

    # Phone frame (a thin border to suggest the device chrome)
    draw.rectangle((40, 130, WIDTH - 40, HEIGHT - 100), outline=INDIGO, width=6)
    # Status bar
    draw.rectangle((40, 130, WIDTH - 40, 220), fill=SURFACE)
    draw.text((70, 155), "Kaavalan  |  v1.9.1  |  vault mode", font=font(28), fill=CHARCOAL)
    # TopAppBar
    draw.rectangle((40, 220, WIDTH - 40, 330), fill=SURFACE, outline=INDIGO, width=2)
    draw.text((70, 248), title, font=font(54), fill=CHARCOAL)
    # Body — 3 mock cards
    card_y = 360
    for i in range(4):
        y0 = card_y + i * 200
        y1 = y0 + 170
        draw.rectangle((70, y0, WIDTH - 70, y1), fill=SURFACE, outline=INDIGO, width=2)
        # Avatar circle
        draw.ellipse((90, y0 + 25, 180, y0 + 115), fill=INDIGO)
        # Name + subtitle
        draw.text(
            (210, y0 + 30),
            f"Person {i + 1}",
            font=font(36),
            fill=CHARCOAL,
        )
        draw.text(
            (210, y0 + 80),
            "Designation  •  Station",
            font=font(24),
            fill=(120, 113, 108),
        )
        # Count badge
        badge_x = WIDTH - 200
        draw.rounded_rectangle(
            (badge_x, y0 + 50, badge_x + 110, y0 + 110),
            radius=20,
            fill=AMBER,
        )
        draw.text(
            (badge_x + 28, y0 + 60),
            f"{i + 2} open",
            font=font(28),
            fill=(255, 255, 255),
        )

    # Caption at the bottom
    draw.rectangle((40, HEIGHT - 100, WIDTH - 40, HEIGHT - 40), fill=INDIGO)
    # Word-wrap the caption into <= 50 chars per line
    words = caption.split()
    lines = []
    line = ""
    for word in words:
        if len(line) + len(word) + 1 > 50:
            lines.append(line)
            line = word
        else:
            line = f"{line} {word}".strip()
    if line:
        lines.append(line)
    for i, ln in enumerate(lines[:2]):
        draw.text(
            (60, HEIGHT - 90 + i * 28),
            ln,
            font=font(22),
            fill=(255, 255, 255),
        )

    img.save(out, format="PNG", optimize=True)


def main() -> None:
    out_dir = Path("docs/play-store-screenshots")
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, title, caption in SCREENS:
        out = out_dir / f"{name}.png"
        draw_screen(out, name, title, caption)
        print(f"  wrote {out}")


if __name__ == "__main__":
    main()
