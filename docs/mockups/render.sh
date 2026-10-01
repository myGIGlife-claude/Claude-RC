#!/usr/bin/env bash
# Regenerates docs/screens/*.png and docs/screens/demo.gif from app.html.
# Needs headless Chromium (Playwright's chrome-headless-shell; override with CHROME=) and Python with Pillow.
set -euo pipefail
cd "$(dirname "$0")"
CH=${CHROME:-$(ls ~/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell | tail -1)}
OUT=../screens; TMP=$(mktemp -d "$PWD/.frames.XXXXXX")  # not /tmp: sandboxed Chromium may not write there
python3 -m http.server 8765 -d . >/dev/null 2>&1 & SRV=$!
trap 'kill $SRV; rm -rf "$TMP"' EXIT; sleep 1
shot(){ "$CH" --no-sandbox --disable-gpu --hide-scrollbars --window-size=412,"$3" --force-device-scale-factor="$4" --screenshot="$1" "http://localhost:8765/app.html?$2" >/dev/null 2>&1; }
for s in accounts plugins tools chat chat-cluster conn-logins conn-mcp conn-keys settings-connection settings-security settings-app; do shot "$OUT/$s.png" "s=$s" 844 2; done
shot "$OUT/accounts-more.png" "s=accounts&scroll=330" 844 2
# demo: 5 fps, 36 s; each frame is a 56 px caption bar + the 844 px app
for i in $(seq 0 179); do shot "$TMP/f$(printf %03d "$i").png" "t=$(python3 -c "print($i/5)")" 900 1; done
python3 - "$TMP" "$OUT/demo.gif" <<'PY'
import sys, glob
from PIL import Image
fr = [Image.open(f).convert("RGB") for f in sorted(glob.glob(sys.argv[1] + "/f*.png"))]
fr = [f.resize((300, round(f.height * 300 / f.width)), Image.LANCZOS) for f in fr]
pal = fr[0].quantize(96, method=Image.Quantize.MEDIANCUT)  # one shared palette keeps the file small
fr = [f.quantize(palette=pal, dither=Image.Dither.NONE) for f in fr]
fr[0].save(sys.argv[2], save_all=True, append_images=fr[1:], duration=200, loop=0, optimize=True)
PY
