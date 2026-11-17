#!/usr/bin/env python3
"""Synthetic ELF/ROM tests for cfru_learnset_witness.py; no real build or ROM."""
import os
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cfru_learnset_witness as w  # noqa: E402

TABLE_OFF = 0x100                      # inside .text
A_OFF, B_OFF, OTHER_OFF, EMPTY_OFF = 0x1800, 0x1810, 0x1840, 0x1850


def learnset(*move_level):
    data = b"".join(struct.pack("<HB", move_level[i], move_level[i + 1]) for i in range(0, len(move_level), 2))
    return data + b"\0\0\xff"


def elf(sections, symbols, phdrs=None):
    """ELF32 LE ARM EXEC. sections: (name, addr, data, type, flags, align); symbols: (name, value,
    size[, section index]); phdrs: PT_LOAD (section index, paddr) pairs, default one per loaded
    section at its VMA. Section 0 is null; .symtab, .strtab and .shstrtab follow the given ones."""
    names = [n for n, *_ in sections] + [".symtab", ".strtab", ".shstrtab"]
    shstr = b"\0" + b"".join(n.encode() + b"\0" for n in names)
    name_at = [1 + sum(len(n) + 1 for n in names[:i]) for i in range(len(names))]
    strtab, syms = b"\0", b"\0" * 16
    for symbol in symbols:
        name, value, size = symbol[:3]
        index = symbol[3] if len(symbol) > 3 else next(
            (i + 1 for i, (_, a, d, *_) in enumerate(sections) if a <= value < a + len(d)), 0xFFF1)
        syms += struct.pack("<IIIBBH", len(strtab), value, size, 1, 0, index)
        strtab += name.encode() + b"\0"
    count = len(sections) + 4
    if phdrs is None:
        phdrs = [(i + 1, sections[i][1]) for i in range(len(sections)) if sections[i][4] & 2]
    body, offsets, at = b"", [], 52 + 32 * len(phdrs)
    for data in [d for _, _, d, *_ in sections] + [syms, strtab, shstr]:
        offsets.append(at + len(body)); body += data
    headers = [(0,) * 10]
    for i, (_, addr, data, kind, flags, align) in enumerate(sections):
        headers.append((name_at[i], kind, flags, addr, offsets[i], len(data), 0, 0, align, 0))
    n = len(sections)
    headers += [(name_at[n], 2, 0, 0, offsets[n], len(syms), n + 2, 1, 4, 16),
                (name_at[n + 1], 3, 0, 0, offsets[n + 1], len(strtab), 0, 0, 1, 0),
                (name_at[n + 2], 3, 0, 0, offsets[n + 2], len(shstr), 0, 0, 1, 0)]
    program = b"".join(struct.pack("<IIIIIIII", 1, offsets[i - 1], sections[i - 1][1], paddr,
                                   len(sections[i - 1][2]), len(sections[i - 1][2]), 5, 4) for i, paddr in phdrs)
    header = b"\x7fELF\x01\x01\x01" + b"\0" * 9 + struct.pack(
        "<HHIIIIIHHHHHH", 2, 40, 1, w.TEXT_VMA, 52, 52 + len(program) + len(body), 0, 52, 32, len(phdrs), 40,
        count, count - 1)
    return header + program + body + b"".join(struct.pack("<IIIIIIIIII", *h) for h in headers)


class Build:
    """A synthetic linked CFRU image and an input ROM with its objcopy image at 0x1000000.
    The table is in .text, A and B in .data, the rest in .rodata.cst4 after a zero gap."""

    def __init__(self):
        self.text = bytearray(0x2000)                  # the whole objcopy -O binary image
        self.text[A_OFF:A_OFF + 9] = learnset(10, 1, 20, 5)
        self.text[B_OFF:B_OFF + 6] = learnset(30, 1)
        self.text[OTHER_OFF:OTHER_OFF + 6] = learnset(40, 1)
        for slot, off in ((1, A_OFF), (2, A_OFF), (3, B_OFF)):        # slot 2 shares slot 1's learnset
            struct.pack_into("<I", self.text, TABLE_OFF + slot * 4, w.TEXT_VMA + off)
        self.symbols = [("gLevelUpLearnsets", w.TEXT_VMA + TABLE_OFF, 1440 * 4),
                        ("sBulbasaurLevelUpLearnset", w.TEXT_VMA + A_OFF, 9),
                        ("sCharmanderLevelUpLearnset", w.TEXT_VMA + B_OFF, 6),
                        ("sOtherData", w.TEXT_VMA + OTHER_OFF, 6)]
        # (name, start, end, type, flags, align) as image offsets.
        self.layout = [(".text", 0, 0x1800, 1, 6, 4), (".data", 0x1800, 0x1830, 1, 3, 4),
                       (".rodata.cst4", 0x1840, 0x2000, 1, 2, 4)]
        self.extra = []
        self.phdrs = None

    def elf(self):
        sections = [(n, w.TEXT_VMA + a, bytes(self.text[a:e]), t, f, al) for n, a, e, t, f, al in self.layout]
        return elf(sections + self.extra, self.symbols, self.phdrs)

    def binary(self):
        return bytes(self.text)

    def rom(self):
        rom = bytearray(w.INSERT_START + len(self.text))
        rom[0xAC:0xB0] = b"BPRE"
        rom[w.INSERT_START:] = self.text
        struct.pack_into("<I", rom, w.ROOT, w.TEXT_VMA + TABLE_OFF)
        return rom


class WitnessTest(unittest.TestCase):
    def outcome(self, build, rom=None):
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(build.elf(), build.binary(), bytes(rom if rom is not None else build.rom()))
        return ctx.exception.result, ctx.exception.category

    def test_exact_linked_objects_and_shared_pointers_pass(self):
        b = Build()
        table, owners = w.derive(b.elf(), b.binary(), bytes(b.rom()))
        self.assertEqual(table, w.INSERT_START + TABLE_OFF)
        # Shared A once, B; the unreferenced and wrongly named sOtherData is not an owner.
        self.assertEqual(owners, [(w.INSERT_START + A_OFF, w.INSERT_START + A_OFF + 9),
                                  (w.INSERT_START + B_OFF, w.INSERT_START + B_OFF + 6)])

    def test_shared_empty_moveset_passes(self):
        # Table in .text, A and B in .data, sEmptyMoveset in .rodata.cst4 after a zero gap.
        b = Build()
        b.text[EMPTY_OFF:EMPTY_OFF + 3] = b"\0\0\xff"
        b.symbols.append(("sEmptyMoveset", w.TEXT_VMA + EMPTY_OFF, 3))
        for slot in (0, 4, 252, 1439):                                        # several slots share it
            struct.pack_into("<I", b.text, TABLE_OFF + slot * 4, w.TEXT_VMA + EMPTY_OFF)
        table, owners = w.derive(b.elf(), b.binary(), bytes(b.rom()))
        self.assertIn((w.INSERT_START + EMPTY_OFF, w.INSERT_START + EMPTY_OFF + 3), owners)
        self.assertEqual(len(owners), 3)
        rom = b.rom(); rom[w.INSERT_START + EMPTY_OFF + 2] = 0                # ROM must still equal the build
        self.assertEqual(self.outcome(b, rom), ("FAIL", "OBJECT_BYTES"))

    def test_empty_moveset_must_be_exactly_one_terminator(self):
        # A valid one-move learnset, or a lone non-terminator, under the empty name.
        for size, data in ((6, b"\x01\0\x05\0\0\xff"), (3, b"\x01\0\xff")):
            b = Build()
            b.text[EMPTY_OFF:EMPTY_OFF + len(data)] = data
            b.symbols.append(("sEmptyMoveset", w.TEXT_VMA + EMPTY_OFF, size))
            struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + EMPTY_OFF)
            self.assertEqual(self.outcome(b), ("FAIL", "OBJECT_INVALID"))
        b = Build()                                                           # another single-terminator symbol
        b.text[EMPTY_OFF:EMPTY_OFF + 3] = b"\0\0\xff"
        b.symbols.append(("sOtherData", w.TEXT_VMA + EMPTY_OFF, 3))
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + EMPTY_OFF)
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))

    def test_missing_zero_or_unnamed_symbols_fail(self):
        b = Build()
        b.symbols = [s for s in b.symbols if s[0] != "gLevelUpLearnsets"]
        self.assertEqual(self.outcome(b), ("FAIL", "SYMBOL_MISSING"))
        b = Build()
        b.symbols = [s for s in b.symbols if s[0] != "sCharmanderLevelUpLearnset"]
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))
        b = Build()
        b.symbols[2] = ("sCharmanderLevelUpLearnset", w.TEXT_VMA + B_OFF, 0)      # zero size is not an object
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))
        b = Build()
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + OTHER_OFF)  # not a learnset symbol
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))
        b = Build()
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + A_OFF + 3)   # inside, not at a start
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))

    def test_aliases_truncation_and_overlap_fail(self):
        b = Build()
        b.symbols.append(("sAliasLevelUpLearnset", w.TEXT_VMA + A_OFF, 6))           # same start, other extent
        self.assertEqual(self.outcome(b), ("FAIL", "SYMBOL_AMBIGUOUS"))
        b = Build()
        b.symbols[1] = ("sBulbasaurLevelUpLearnset", w.TEXT_VMA + A_OFF, 6)        # truncated before terminator
        self.assertEqual(self.outcome(b), ("FAIL", "OBJECT_INVALID"))
        b = Build()
        b.symbols[1] = ("sBulbasaurLevelUpLearnset", w.TEXT_VMA + A_OFF, 0x16)     # runs into B
        self.assertEqual(self.outcome(b), ("FAIL", "OBJECT_INVALID"))
        b = Build()
        b.text[B_OFF - 3:B_OFF + 6] = learnset(50, 1, 30, 1)[0:3] + learnset(30, 1)
        b.symbols.append(("sPichuLevelUpLearnset", w.TEXT_VMA + B_OFF - 3, 9))
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + B_OFF - 3)   # overlaps B
        self.assertEqual(self.outcome(b), ("FAIL", "OBJECT_OVERLAP"))

    def test_wrong_image_placement_or_layout_fail(self):
        b = Build()
        self.assertEqual(self.outcome(b, elf_rom_shifted(b)), ("FAIL", "ROOT_MISMATCH"))
        cases = {
            "loaded below .text": lambda b: b.extra.append((".ewram", 0x02000000, b"\0" * 4, 1, 3, 4)),
            "overlapping sections": lambda b: b.layout.__setitem__(1, (".data", 0x17F0, 0x1830, 1, 3, 4)),
            "load address is not the VMA": lambda b: setattr(b, "phdrs", [(1, w.TEXT_VMA), (2, w.TEXT_VMA + 0x10000),
                                                                           (3, w.TEXT_VMA + 0x1840)]),
            "section in no segment": lambda b: setattr(b, "phdrs", [(1, w.TEXT_VMA), (3, w.TEXT_VMA + 0x1840)]),
            "misaligned section": lambda b: b.layout.__setitem__(1, (".data", 0x1800, 0x1830, 1, 3, 0x1000)),
            "unknown loaded section type": lambda b: b.layout.__setitem__(2, (".init_array", 0x1840, 0x2000, 14, 3, 4)),
            "symbol crosses its section": lambda b: b.symbols.append(("sXLevelUpLearnset", w.TEXT_VMA + 0x182D, 6)),
            "first section is not .text": lambda b: b.layout.__setitem__(0, (".rodata", 0, 0x1800, 1, 2, 4)),
        }
        for label, mutate in cases.items():
            b = Build()
            mutate(b)
            self.assertEqual(self.outcome(b), ("FAIL", "ELF_LAYOUT"), label)
        b = Build()                                     # the image is not linked at the CFRU insert
        sections = [(n, 0x08800000 + a, bytes(b.text[a:e]), t, f, al) for n, a, e, t, f, al in b.layout]
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(elf(sections, b.symbols), b.binary(), bytes(b.rom()))
        self.assertEqual(ctx.exception.category, "ELF_LAYOUT")
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(b"not an elf", b"", bytes(b.rom()))
        self.assertEqual(ctx.exception.category, "ELF_LAYOUT")

    def test_output_bin_must_be_this_elfs_image(self):
        b = Build()
        for binary in (b.binary()[:0x1800], b.binary() + b"\0", b.binary()[:-1] + b"\1"):
            with self.assertRaises(w.Outcome) as ctx:
                w.derive(b.elf(), binary, bytes(b.rom()))
            self.assertEqual(ctx.exception.category, "IMAGE_MISMATCH")

    def test_pointers_outside_loaded_sections_are_unmapped(self):
        b = Build()                                     # a learnset symbol in a section objcopy does not emit
        b.extra.append((".comment", w.TEXT_VMA + 0x3000, learnset(60, 1), 1, 0, 1))
        b.symbols.append(("sCommentLevelUpLearnset", w.TEXT_VMA + 0x3000, 6, 4))
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + 0x3000)
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))
        b = Build()
        struct.pack_into("<I", b.text, TABLE_OFF + 4 * 4, w.TEXT_VMA + 0x1830)   # the zero gap
        self.assertEqual(self.outcome(b), ("FAIL", "UNMAPPED_POINTER"))

    def test_wrong_input_root_table_or_bytes_fail(self):
        b = Build()
        rom = b.rom(); rom[0xAC:0xB0] = b"AXVE"
        self.assertEqual(self.outcome(b, rom), ("FAIL", "ROM_IDENTITY"))
        rom = b.rom(); struct.pack_into("<I", rom, w.ROOT, w.TEXT_VMA + 0x200)
        self.assertEqual(self.outcome(b, rom), ("FAIL", "ROOT_MISMATCH"))
        rom = b.rom(); rom[w.INSERT_START + TABLE_OFF + 12] ^= 1
        self.assertEqual(self.outcome(b, rom), ("FAIL", "TABLE_BYTES"))
        rom = b.rom(); rom[w.INSERT_START + A_OFF] ^= 1                             # tampered move
        self.assertEqual(self.outcome(b, rom), ("FAIL", "OBJECT_BYTES"))
        rom = b.rom(); rom[w.INSERT_START + B_OFF + 5] = 0                          # tampered terminator
        self.assertEqual(self.outcome(b, rom), ("FAIL", "OBJECT_BYTES"))

    def test_run_writes_only_on_pass_and_never_overwrites(self):
        b = Build()
        with tempfile.TemporaryDirectory() as d:
            os.makedirs(os.path.join(d, "build"))
            with open(os.path.join(d, "build", "linked.o"), "wb") as f:
                f.write(b.elf())
            with open(os.path.join(d, "build", "output.bin"), "wb") as f:
                f.write(b.binary())
            rom_path, out = os.path.join(d, "in.gba"), os.path.join(d, "witness.properties")
            with open(rom_path, "wb") as f:
                f.write(b.rom())
            w.run(d, rom_path, out, revision=lambda repo: None)
            with open(out) as f:
                text = f.read()
            self.assertIn("schema=LEARNSET_REORDER_WITNESS_V1\n", text)
            self.assertIn("objects.count=2\n", text)
            with self.assertRaises(w.Outcome) as ctx:
                w.run(d, rom_path, out, revision=lambda repo: None)
            self.assertEqual(ctx.exception.category, "OUTPUT_EXISTS")

            def stale(repo):
                raise w.fail("SOURCE_REVISION")
            out2 = os.path.join(d, "second.properties")
            with self.assertRaises(w.Outcome) as ctx:
                w.run(d, rom_path, out2, revision=stale)
            self.assertEqual(ctx.exception.category, "SOURCE_REVISION")
            with self.assertRaises(w.Outcome) as ctx:
                w.run(d, os.path.join(d, "missing.gba"), out2, revision=lambda repo: None)
            self.assertEqual((ctx.exception.result, ctx.exception.category), ("UNKNOWN", "UNREADABLE_EVIDENCE"))
            self.assertFalse(os.path.exists(out2))
            rom = b.rom(); rom[w.INSERT_START + A_OFF] ^= 1
            with open(rom_path, "wb") as f:
                f.write(rom)
            with self.assertRaises(w.Outcome):
                w.run(d, rom_path, out2, revision=lambda repo: None)
            self.assertFalse(os.path.exists(out2))


def elf_rom_shifted(build):
    """The same image installed one word later: the runtime root no longer names this table."""
    rom = build.rom()
    rom[w.INSERT_START + 4:] = rom[w.INSERT_START:-4]
    struct.pack_into("<I", rom, w.ROOT, w.TEXT_VMA + TABLE_OFF + 4)
    return rom


if __name__ == "__main__":
    unittest.main()
