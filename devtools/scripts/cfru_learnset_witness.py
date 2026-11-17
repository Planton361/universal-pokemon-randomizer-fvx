#!/usr/bin/env python3
"""USER-ONLY: derive a LEARNSET_REORDER_WITNESS_V1 for the CFRU/DPE in-place learnset reorder.

Reads, never writes, the exact CFRU checkout's build/linked.o and the untouched input ROM.
CFRU links its insert at 0x09000000 (linker.ld) and scripts/insert.py writes the objcopy'd
.text at ROM offset 0x1000000, so a symbol at VMA v is installed at v - 0x08000000. Owners
are only the gLevelUpLearnsets table and the compiled s*LevelUpLearnset objects (plus the
single-terminator sEmptyMoveset) it points to, with ELF sizes, and only if their installed bytes equal the linked bytes exactly.

Prints PASS, FAIL <CATEGORY> or UNKNOWN <CATEGORY>; never paths, hashes or bytes. The
witness file is written only on PASS and stays local. It is not full D1 provenance.
"""
import argparse
import hashlib
import os
import re
import struct
import subprocess
import sys

CFRU_SHA = "196058edbc2b8b7d8c725b715b1f967ba60f5c83"
DPE_SHA = "d887185de1f6ae6a78e85c4311bbadde17041d00"
TEXT_VMA = 0x09000000
INSERT_START, INSERT_END = 0x1000000, 0x1600000
ROOT = 0x43E20
SLOTS = 1440
ENTRY, MAX_ENTRIES = 3, 51
OBJECT_NAME = re.compile(r"s\w*LevelUpLearnset")
EMPTY_NAME, EMPTY_BYTES = "sEmptyMoveset", b"\0\0\xff"


class Outcome(Exception):
    def __init__(self, result, category):
        super().__init__(category)
        self.result, self.category = result, category


def fail(category):
    return Outcome("FAIL", category)


def check_revision(repo):
    """The linked object must belong to a clean checkout of the pinned CFRU revision."""
    try:
        head = subprocess.run(["git", "-C", repo, "rev-parse", "HEAD"], capture_output=True, text=True, check=True)
        dirty = subprocess.run(["git", "-C", repo, "status", "--porcelain", "--untracked-files=no"],
                               capture_output=True, text=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        raise Outcome("UNKNOWN", "SOURCE_REVISION")
    if head.stdout.strip() != CFRU_SHA or dirty.stdout.strip():
        raise fail("SOURCE_REVISION")


def parse_elf(elf):
    """The single allocated .text image at TEXT_VMA and its sized symbols."""
    if len(elf) < 52 or elf[:4] != b"\x7fELF" or elf[4] != 1 or elf[5] != 1:
        raise fail("ELF_LAYOUT")
    e_type, e_machine = struct.unpack_from("<HH", elf, 16)
    e_shoff, = struct.unpack_from("<I", elf, 32)
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from("<HHH", elf, 46)
    if e_type != 2 or e_machine != 40 or e_shentsize != 40 or not 0 < e_shnum <= 4096 \
            or e_shstrndx >= e_shnum or e_shoff + e_shnum * 40 > len(elf):
        raise fail("ELF_LAYOUT")
    sections = [struct.unpack_from("<IIIIIIIIII", elf, e_shoff + i * 40) for i in range(e_shnum)]
    for s in sections:
        if s[1] != 8 and s[4] + s[5] > len(elf):           # every non-NOBITS section within the file
            raise fail("ELF_LAYOUT")
    names = sections[e_shstrndx]

    def name(table, offset):
        end = elf.find(b"\0", table[4] + offset, table[4] + table[5])
        if offset >= table[5] or end < 0:
            raise fail("ELF_LAYOUT")
        return elf[table[4] + offset:end].decode("ascii", "replace")

    # objcopy -O binary emits every allocated PROGBITS section: only .text may contribute.
    loaded = [i for i, s in enumerate(sections) if s[1] == 1 and s[2] & 2 and s[5]]
    if len(loaded) != 1 or name(names, sections[loaded[0]][0]) != ".text" or sections[loaded[0]][3] != TEXT_VMA:
        raise fail("ELF_LAYOUT")
    text_index = loaded[0]
    text = sections[text_index]
    image = elf[text[4]:text[4] + text[5]]
    symtabs = [s for s in sections if s[1] == 2]
    if len(symtabs) != 1 or symtabs[0][9] != 16 or symtabs[0][6] >= e_shnum:
        raise fail("ELF_LAYOUT")
    symtab, strtab = symtabs[0], sections[symtabs[0][6]]
    symbols = []
    for at in range(symtab[4], symtab[4] + symtab[5] - 15, 16):
        st_name, value, size, info, _, shndx = struct.unpack_from("<IIIBBH", elf, at)
        if st_name and shndx == text_index and info & 15 == 1 and size:   # sized STT_OBJECT in .text
            symbols.append((name(strtab, st_name), value, size))
    return image, symbols


def terminated_once(data):
    """Exactly one learnset: the only {0, 0xFF} entry is the last one."""
    entries = [data[i:i + ENTRY] for i in range(0, len(data), ENTRY)]
    return all((e == b"\0\0\xff") == (i == len(entries) - 1) for i, e in enumerate(entries))


def derive(elf, rom):
    """Validated (table, objects) owners as ROM offsets; raises Outcome otherwise."""
    image, symbols = parse_elf(elf)
    text_end = TEXT_VMA + len(image)

    def linked(vma, size):
        if vma < TEXT_VMA or vma + size > text_end:
            raise fail("OBJECT_INVALID")
        return image[vma - TEXT_VMA:vma - TEXT_VMA + size]

    tables = [s for s in symbols if s[0] == "gLevelUpLearnsets"]
    if not tables:
        raise fail("SYMBOL_MISSING")
    if len(tables) != 1 or tables[0][2] != SLOTS * 4 or tables[0][1] % 4:
        raise fail("SYMBOL_AMBIGUOUS")
    table_vma = tables[0][1]
    table_bytes = linked(table_vma, SLOTS * 4)

    if len(rom) < INSERT_START + len(image) or rom[0xAC:0xB0] != b"BPRE" or rom[0xBC] != 0:
        raise fail("ROM_IDENTITY")
    if INSERT_START + len(image) > INSERT_END:
        raise fail("ELF_LAYOUT")
    if struct.unpack_from("<I", rom, ROOT)[0] != table_vma:
        raise fail("ROOT_MISMATCH")
    table = table_vma - 0x08000000
    if rom[table:table + SLOTS * 4] != table_bytes:
        raise fail("TABLE_BYTES")

    candidates = {}
    for symbol, value, size in symbols:
        if symbol == EMPTY_NAME:
            # CFRU's shared empty learnset: exactly one terminator and nothing else.
            if size != len(EMPTY_BYTES) or linked(value, size) != EMPTY_BYTES:
                raise fail("OBJECT_INVALID")
            candidates.setdefault(value, set()).add(size)
        elif OBJECT_NAME.fullmatch(symbol):
            candidates.setdefault(value, set()).add(size)
    objects = set()
    for slot in range(SLOTS):
        pointer, = struct.unpack_from("<I", table_bytes, slot * 4)
        if pointer == 0:
            continue
        sizes = candidates.get(pointer)
        if not sizes:
            raise fail("UNMAPPED_POINTER")
        if len(sizes) != 1:
            raise fail("SYMBOL_AMBIGUOUS")
        size = next(iter(sizes))
        if size % ENTRY or size > MAX_ENTRIES * ENTRY or not terminated_once(linked(pointer, size)):
            raise fail("OBJECT_INVALID")
        objects.add((pointer, size))

    owners = sorted((vma - 0x08000000, vma - 0x08000000 + size) for vma, size in objects)
    previous = INSERT_START
    for start, end in owners:
        if start < previous or (start < table + SLOTS * 4 and table < end):
            raise fail("OBJECT_OVERLAP")
        if rom[start:end] != linked(start + 0x08000000, end - start):
            raise fail("OBJECT_BYTES")
        previous = end
    if not owners:
        raise fail("SYMBOL_MISSING")
    return table, owners


def witness_text(elf, rom, table, owners):
    lines = ["schema=LEARNSET_REORDER_WITNESS_V1", "version=1", "cfru.sha=" + CFRU_SHA, "dpe.sha=" + DPE_SHA,
             "elf.sha256=" + hashlib.sha256(elf).hexdigest(), "input.size=%d" % len(rom),
             "input.sha256=" + hashlib.sha256(rom).hexdigest(),
             "table.start=0x%x" % table, "table.end=0x%x" % (table + SLOTS * 4), "objects.count=%d" % len(owners)]
    for i, (start, end) in enumerate(owners):
        lines += ["objects.%d.start=0x%x" % (i, start), "objects.%d.end=0x%x" % (i, end)]
    return "\n".join(lines) + "\n"


def run(repo, rom_path, out_path, revision=check_revision):
    if os.path.lexists(out_path):
        raise fail("OUTPUT_EXISTS")
    revision(repo)
    try:
        with open(os.path.join(repo, "build", "linked.o"), "rb") as f:
            elf = f.read()
        with open(rom_path, "rb") as f:
            rom = f.read()
    except OSError:
        raise Outcome("UNKNOWN", "UNREADABLE_EVIDENCE")
    table, owners = derive(elf, rom)
    fd = os.open(out_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="ascii") as f:
        f.write(witness_text(elf, rom, table, owners))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cfru-repo", required=True, help="clean CFRU checkout at the pinned revision, built")
    parser.add_argument("--rom", required=True, help="the untouched pilot input ROM (read only)")
    parser.add_argument("--out", required=True, help="new local witness file (never commit or share)")
    args = parser.parse_args(argv)
    try:
        run(args.cfru_repo, args.rom, args.out)
    except Outcome as outcome:
        print(outcome.result, outcome.category)
        return 1 if outcome.result == "FAIL" else 2
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
