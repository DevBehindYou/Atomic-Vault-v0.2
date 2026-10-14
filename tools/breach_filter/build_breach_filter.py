#!/usr/bin/env python3
"""
Builds app/src/main/assets/breached_passwords.bloom, the offline breach list
read by com.example.security.BreachedPasswords.

Source: SecLists Passwords/Common-Credentials/Pwdb_top-1000000.txt (MIT
licence, https://github.com/danielmiessler/SecLists), the 1,000,000 most
common passwords in public breach compilations. The file is pinned by
SHA-256 below; the build refuses any other content.

Format (big-endian):
  "AVBF" | version u8 (1) | k u8 | reserved u16 | m (bits) u64 | n u32 | bit array
Index i of a password: d = SHA-256(UTF-8 password); h1 = d[0:8], h2 = d[8:16] | 1
(unsigned 64-bit); bit = (h1 + i * h2) mod 2^64 mod m, for i in 0..k-1.
Bit b is (byte[b >> 3] >> (b & 7)) & 1. Passwords are matched exactly.

  usage: build_breach_filter.py <Pwdb_top-1000000.txt> <out.bloom>
"""
import hashlib
import math
import struct
import sys

SOURCE_SHA256 = "e9a88f67aafe65496682dc374559ee714e978bee50314767494c3e37a18c9fc8"
FALSE_POSITIVE_RATE = 0.001
MASK64 = (1 << 64) - 1


def indices(password: str, k: int, m: int):
    d = hashlib.sha256(password.encode("utf-8")).digest()
    h1 = int.from_bytes(d[0:8], "big")
    h2 = int.from_bytes(d[8:16], "big") | 1
    return [((h1 + i * h2) & MASK64) % m for i in range(k)]


def main(src: str, out: str) -> None:
    raw = open(src, "rb").read()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != SOURCE_SHA256:
        sys.exit(f"source SHA-256 {digest} does not match the pinned {SOURCE_SHA256}")
    words = []
    seen = set()
    for line in raw.decode("utf-8", errors="strict").split("\n"):
        w = line.rstrip("\r")
        if w and w not in seen:
            seen.add(w)
            words.append(w)
    n = len(words)
    m = math.ceil(-n * math.log(FALSE_POSITIVE_RATE) / (math.log(2) ** 2))
    m = (m + 7) // 8 * 8
    k = max(1, round(m / n * math.log(2)))
    bits = bytearray(m // 8)
    for w in words:
        for b in indices(w, k, m):
            bits[b >> 3] |= 1 << (b & 7)
    with open(out, "wb") as f:
        f.write(b"AVBF" + struct.pack(">BBHQI", 1, k, 0, m, n) + bytes(bits))
    print(f"{n} passwords, m={m} bits ({m // 8} bytes), k={k}, target false-positive rate {FALSE_POSITIVE_RATE}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
