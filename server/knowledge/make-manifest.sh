#!/usr/bin/env bash
# Writes (or with --check, verifies) MANIFEST: "<sha256>  <path>" for VERSION and every skills/rc-*/ markdown file.
# install.sh downloads MANIFEST, then each file, and refuses any file whose checksum differs.
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
out="$(
  { echo VERSION; find skills -type f \( -name 'SKILL.md' -o -path 'skills/rc-*/references/*.md' \) | sort; } |
    while IFS= read -r f; do printf '%s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$f"; done
)"
if [[ "${1:-}" == --check ]]; then
  [[ "$out" == "$(cat MANIFEST 2>/dev/null)" ]] || { echo "MANIFEST is out of date: run server/knowledge/make-manifest.sh" >&2; exit 1; }
else
  printf '%s\n' "$out" >MANIFEST
  echo "MANIFEST: $(wc -l <MANIFEST) files"
fi
