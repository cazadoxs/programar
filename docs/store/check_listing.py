#!/usr/bin/env python3
"""Comprueba los límites de caracteres de las fichas de Google Play.

Uso: python3 docs/store/check_listing.py
Lee ficha-es.md y listing-en.md, busca cada encabezado "### ... (máx. N)" /
"### ... (max N)" y cuenta los caracteres del bloque ```text que lo sigue.
"""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ok = True
for name in ("ficha-es.md", "listing-en.md"):
    path = HERE / name
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    print(f"\n{name}")
    for m in re.finditer(r"^### (.+?)\((?:máx\.|max\.?) (\d+)\)\s*\n+```text\n(.*?)\n```", text, re.S | re.M):
        label, limit, body = m.group(1).strip(), int(m.group(2)), m.group(3)
        n = len(body)
        # Play cuenta algunos emoji como 2 (pares sustitutos UTF-16): se mide también así.
        n16 = len(body.encode("utf-16-le")) // 2
        status = "OK " if n16 <= limit else "MAL"
        ok &= n16 <= limit
        print(f"  [{status}] {label}: {n} caracteres ({n16} en UTF-16) / {limit}")
sys.exit(0 if ok else 1)
