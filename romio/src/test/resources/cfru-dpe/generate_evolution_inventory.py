"""Regenerate source-only #667 fixture from the exact DPE checkout; no ROM input."""
import ast
from collections import Counter
from pathlib import Path
import re
import subprocess
import sys

DPE_PIN = 'd887185de1f6ae6a78e85c4311bbadde17041d00'
dpe = Path(sys.argv[1])
assert subprocess.check_output(['git', '-C', str(dpe), 'rev-parse', 'HEAD'], text=True).strip() == DPE_PIN

def clean(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)

symbols = {'TRUE': '1', 'FALSE': '0'}
for name in ['species.h', 'items.h', 'moves.h', 'evolution.h', 'base_stats.h']:
    symbols.update(re.findall(r'^\s*#define\s+(\w+)\s+([^\n]+)', clean((dpe / 'include' / name).read_text()), re.M))
evolution = clean((dpe / 'include/evolution.h').read_text())
for enum_name in ['EvolutionMethods', 'MegaEvoVariants']:
    names = re.search(r'enum ' + enum_name + r'\s*\{(.*?)\}', evolution, re.S).group(1)
    for index, name in enumerate(names.split(',')):
        if name.strip(): symbols[name.split('=')[0].strip()] = str(index)

def value(expression):
    expression = re.sub(r'TIME_RANGE\((\d+),\s*(\d+)\)', lambda m: str(int(m[1]) * 256 + int(m[2])), expression)
    expression = re.sub(r'\b[A-Za-z_]\w*\b', lambda m: str(value(symbols[m[0]])), expression.strip())
    tree = ast.parse(expression, mode='eval')
    assert all(isinstance(n, (ast.Expression, ast.Constant, ast.BinOp, ast.UnaryOp, ast.Add, ast.Sub, ast.Mult, ast.LShift, ast.RShift, ast.BitOr, ast.BitAnd, ast.USub, ast.UAdd)) for n in ast.walk(tree)), expression
    return eval(compile(tree, '<source constant>', 'eval'), {'__builtins__': {}})

pool = {}
for line in (Path(__file__).parent / 'species-pool-inventory.tsv').read_text().splitlines():
    if line.startswith('#'): continue
    cols = line.split('\t'); pool[int(cols[0], 16)] = cols[3] == 'YES'
text = clean((dpe / 'src/Evolution Table.c').read_text())
rows = []
starts = list(re.finditer(r'\[(SPECIES_\w+)\]\s*=\s*\{', text))
for index, match in enumerate(starts):
    source_name = match[1]
    body = text[match.end():starts[index + 1].start() if index + 1 < len(starts) else len(text)]
    source = value(source_name)
    entries = re.findall(r'\{([^{}]+)\}', body + '}')
    for slot, entry in enumerate(entries):
        parts = re.split(r',\s*(?![^()]*\))', entry)
        assert len(parts) == 4, entry
        method, parameter, target, auxiliary = map(value, parts)
        rows.append([source, slot, method, parameter, target, auxiliary, source_name, parts[0].strip(), parts[2].strip()])
assert len({(r[0], r[1]) for r in rows}) == len(rows)
counts = Counter((r[0], *r[2:6]) for r in rows)
for row in rows:
    source, slot, method, parameter, target, auxiliary = row[:6]
    if method in (253, 254): disposition = 'TRANSFORMATION_PRESERVE_RAW'
    elif not 1 <= method <= 42: disposition = 'UNKNOWN_PRESERVE_RAW' if method else 'INVALID_OR_PLACEHOLDER_PRESERVE_RAW'
    elif not pool.get(target, False) or counts[(source, method, parameter, target, auxiliary)] > 1: disposition = 'INVALID_OR_PLACEHOLDER_PRESERVE_RAW'
    else: disposition = 'ORDINARY_TARGET_RANDOMIZABLE'
    row.append(disposition)
out = Path(__file__).parents[3] / 'testFixtures/resources/cfru-dpe/evolution-slot-inventory.tsv'
out.write_text('# DPE ' + DPE_PIN + '; source-only Evolution Table.c + include constants\n# source\tslot\tmethod\tparam\ttarget\taux\tsource constant\tmethod constant\ttarget constant\tdisposition\n' + '\n'.join('\t'.join(map(str, row)) for row in rows) + '\n')
print('Populated', len(rows), 'Methods', sorted(Counter(r[2] for r in rows).items()))
print('Classification', Counter(r[-1] for r in rows))
