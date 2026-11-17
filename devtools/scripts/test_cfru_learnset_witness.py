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
A_OFF, B_OFF, OTHER_OFF = 0x1800, 0x1810, 0x1840


def learnset(*move_level):
    data = b"".join(struct.pack("<HB", move_level[i], move_level[i + 1]) for i in range(0, len(move_level), 2))
    return data + b"\0\0\xff"


def elf(text, symbols, vma=w.TEXT_VMA, extra_loaded=False):
    """ELF32 LE ARM EXEC: null, .text, [.data], .symtab, .strtab, .shstrtab."""
    shstr = b"\0.text\0.symtab\0.strtab\0.shstrtab\0.data\0"
    strtab = b"\0"
    syms = b"\0" * 16
    for name, value, size in symbols:
        syms += struct.pack("<IIIBBH", len(strtab), value, size, 1, 0, 1)
        strtab += name.encode() + b"\0"
    body = text + syms + strtab + shstr + (b"\0" * 4 if extra_loaded else b"")
    t, s, st, sh = 52, 52 + len(text), 52 + len(text) + len(syms), 52 + len(text) + len(syms) + len(strtab)
    sections = [(0,) * 10, (1, 1, 6, vma, t, len(text), 0, 0, 4, 0)]
    if extra_loaded:
        sections.append((33, 1, 3, 0x02000000, sh + len(shstr), 4, 0, 0, 4, 0))
    symtab_index = len(sections)
    sections += [(7, 2, 0, 0, s, len(syms), symtab_index + 1, 1, 4, 16), (15, 3, 0, 0, st, len(strtab), 0, 0, 1, 0),
                 (23, 3, 0, 0, sh, len(shstr), 0, 0, 1, 0)]
    shoff = 52 + len(body)
    header = b"\x7fELF\x01\x01\x01" + b"\0" * 9 + struct.pack(
        "<HHIIIIIHHHHHH", 2, 40, 1, w.TEXT_VMA, 0, shoff, 0, 52, 0, 0, 40, len(sections), len(sections) - 1)
    return header + body + b"".join(struct.pack("<IIIIIIIIII", *s) for s in sections)


class Build:
    """A synthetic linked CFRU image and an input ROM with it installed at 0x1000000."""

    def __init__(self):
        self.text = bytearray(0x2000)
        self.text[A_OFF:A_OFF + 9] = learnset(10, 1, 20, 5)
        self.text[B_OFF:B_OFF + 6] = learnset(30, 1)
        self.text[OTHER_OFF:OTHER_OFF + 6] = learnset(40, 1)
        for slot, off in ((1, A_OFF), (2, A_OFF), (3, B_OFF)):        # slot 2 shares slot 1's learnset
            struct.pack_into("<I", self.text, TABLE_OFF + slot * 4, w.TEXT_VMA + off)
        self.symbols = [("gLevelUpLearnsets", w.TEXT_VMA + TABLE_OFF, 1440 * 4),
                        ("sBulbasaurLevelUpLearnset", w.TEXT_VMA + A_OFF, 9),
                        ("sCharmanderLevelUpLearnset", w.TEXT_VMA + B_OFF, 6),
                        ("sOtherData", w.TEXT_VMA + OTHER_OFF, 6)]
        self.extra_loaded = False

    def elf(self):
        return elf(bytes(self.text), self.symbols, extra_loaded=self.extra_loaded)

    def rom(self):
        rom = bytearray(w.INSERT_START + len(self.text))
        rom[0xAC:0xB0] = b"BPRE"
        rom[w.INSERT_START:] = self.text
        struct.pack_into("<I", rom, w.ROOT, w.TEXT_VMA + TABLE_OFF)
        return rom


class WitnessTest(unittest.TestCase):
    def outcome(self, build, rom=None):
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(build.elf(), bytes(rom if rom is not None else build.rom()))
        return ctx.exception.result, ctx.exception.category

    def test_exact_linked_objects_and_shared_pointers_pass(self):
        b = Build()
        table, owners = w.derive(b.elf(), bytes(b.rom()))
        self.assertEqual(table, w.INSERT_START + TABLE_OFF)
        # Shared A once, B; the unreferenced and wrongly named sOtherData is not an owner.
        self.assertEqual(owners, [(w.INSERT_START + A_OFF, w.INSERT_START + A_OFF + 9),
                                  (w.INSERT_START + B_OFF, w.INSERT_START + B_OFF + 6)])

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
        b = Build()
        b.extra_loaded = True                                                       # objcopy would emit more
        self.assertEqual(self.outcome(b), ("FAIL", "ELF_LAYOUT"))
        b = Build()
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(elf(bytes(b.text), b.symbols, vma=0x08800000), bytes(b.rom()))
        self.assertEqual(ctx.exception.category, "ELF_LAYOUT")
        with self.assertRaises(w.Outcome) as ctx:
            w.derive(b"not an elf", bytes(b.rom()))
        self.assertEqual(ctx.exception.category, "ELF_LAYOUT")

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
