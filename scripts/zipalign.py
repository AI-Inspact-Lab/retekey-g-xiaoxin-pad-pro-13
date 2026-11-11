#!/usr/bin/env python3
"""A small zipalign, written the way the SDK's zipalign writes an APK.

Every stored (uncompressed) entry's data is made to start on a 4-byte boundary (4096 for native
libraries), which Android requires of resources.arsc and which the memory-mapped Hanja indexes
rely on. Used by build-apk-nogradle.sh when the SDK's own zipalign is missing or will not run.

How the padding is written matters: Android parses the ZIP strictly, and padding must be a
well-formed extra-field block in the *local* header only. This writes the same block the SDK's
zipalign does — header id 0xD935, the alignment as a 16-bit value, then zero bytes — and leaves
the central directory's extra fields empty. (An earlier version padded with 1–3 raw zero bytes
in both headers, which is not a valid extra field and which Android can refuse to parse.)

    zipalign.py <in.apk> <out.apk>
"""
import struct
import sys
import zipfile

ALIGNMENT_EXTRA_ID = 0xD935
LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
CENTRAL_HEADER = struct.Struct("<IHHHHHHIIIHHHHHII")
END_RECORD = struct.Struct("<IHHHHIIH")


def _want(name):
    return 4096 if name.endswith(".so") else 4


def align(src, dst):
    with open(src, "rb") as f:
        raw = f.read()
    entries = zipfile.ZipFile(src).infolist()
    out = bytearray()
    central = bytearray()
    for info in entries:
        lh = info.header_offset
        (sig, version, flags, method, mtime, mdate, crc, csize, usize, nlen,
         elen) = LOCAL_HEADER.unpack_from(raw, lh)
        if sig != 0x04034B50:
            raise ValueError("bad local header for " + info.filename)
        if flags & 0x0008:
            raise ValueError("data descriptors are not supported: " + info.filename)
        name = raw[lh + 30:lh + 30 + nlen]
        data_start = lh + 30 + nlen + elen
        data = raw[data_start:data_start + info.compress_size]

        offset = len(out)
        extra = b""
        if method == 0:
            want = _want(info.filename)
            header_end = offset + 30 + nlen
            # The block is at least 6 bytes (id, size, alignment); pad it so the data lands aligned.
            pad = (-(header_end + 6)) % want
            extra = struct.pack("<HHH", ALIGNMENT_EXTRA_ID, 2 + pad, want) + b"\0" * pad
        out += LOCAL_HEADER.pack(0x04034B50, version, flags, method, mtime, mdate, crc,
                                 info.compress_size, info.file_size, nlen, len(extra))
        out += name + extra + data

        central += CENTRAL_HEADER.pack(
            0x02014B50, (info.create_system << 8) | info.create_version, version, flags,
            method, mtime, mdate, crc, info.compress_size, info.file_size, nlen,
            0,      # no extra field in the central directory
            0, 0, info.internal_attr, info.external_attr, offset)
        central += name

    cd_offset = len(out)
    out += central
    out += END_RECORD.pack(0x06054B50, 0, 0, len(entries), len(entries), len(central),
                           cd_offset, 0)
    with open(dst, "wb") as f:
        f.write(out)


def check(path):
    """Problems a strict parser would object to; empty when the file is clean."""
    problems = []
    raw = open(path, "rb").read()
    for info in zipfile.ZipFile(path).infolist():
        lh = info.header_offset
        nlen, elen = struct.unpack_from("<HH", raw, lh + 26)
        start = lh + 30 + nlen + elen
        if info.compress_type == zipfile.ZIP_STORED and start % _want(info.filename):
            problems.append("misaligned: " + info.filename)
        for where, extra in (("local", raw[lh + 30 + nlen:start]), ("central", info.extra)):
            pos = 0
            while pos < len(extra):
                if len(extra) - pos < 4:
                    problems.append(f"malformed {where} extra field: {info.filename}")
                    break
                _, size = struct.unpack_from("<HH", extra, pos)
                pos += 4 + size
                if pos > len(extra):
                    problems.append(f"overlong {where} extra field: {info.filename}")
                    break
    return problems


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--check":
        found = check(sys.argv[2])
        print("\n".join(found) if found else "clean: " + sys.argv[2])
        sys.exit(1 if found else 0)
    align(sys.argv[1], sys.argv[2])
    found = check(sys.argv[2])
    if found:
        sys.exit("\n".join(found))
    print("aligned:", sys.argv[2])
