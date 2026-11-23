#!/usr/bin/env python3
"""ADMIN_PASSWORD və VAPID açarlarını .secrets.local faylına yazır (ekrana yazmır)."""
import base64
import os
import secrets
import sys

OUT = ".secrets.local"
if os.path.exists(OUT):
    sys.exit(OUT + " artıq var. Əvvəl silin: rm .secrets.local")
try:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ec
except ImportError:
    sys.exit("Əvvəl quraşdırın: pip install --user cryptography")


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


key = ec.generate_private_key(ec.SECP256R1())
private = key.private_numbers().private_value.to_bytes(32, "big")
public = key.public_key().public_bytes(
    serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)

fd = os.open(OUT, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as f:
    f.write("ADMIN_PASSWORD=" + secrets.token_urlsafe(18) + "\n")
    f.write("VAPID_PUBLIC_KEY=" + b64(public) + "\n")
    f.write("VAPID_PRIVATE_KEY=" + b64(private) + "\n")
print("Yaradıldı: .secrets.local. Redaktorda açın, dəyərləri platformaya köçürün, sonra silin.")
