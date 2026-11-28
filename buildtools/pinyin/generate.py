"""Generate from a local Unicode 17.0.0 Unihan.zip; no network or third-party modules."""
import argparse
from collections import defaultdict
from hashlib import sha256
from pathlib import Path
import re
import struct
import unicodedata
import zipfile

SOURCE_SHA256 = 'f7a48b2b545acfaa77b2d607ae28747404ce02baefee16396c5d2d7a8ef34b5e'
HERE = Path(__file__).resolve().parent
DEFAULT_OUTPUT = HERE.parents[1] / 'Common/src/main/resources/assets/jei/search/pinyin_dict.bin'


def records(archive, name):
    for line in archive.read('Unihan_' + name + '.txt').decode().splitlines():
        if line.startswith('U+'):
            point, field, value = line.split('\t')
            yield int(point[2:], 16), field, value


def syllable(value):
    decomposed = unicodedata.normalize('NFD', value).replace('u\u0308', 'v')
    value = ''.join(c for c in decomposed if not unicodedata.combining(c))
    if not re.fullmatch('[a-z]+', value):
        raise ValueError(value)
    return value


def varint(n):
    result = bytearray()
    while n >= 128:
        result.append((n & 127) | 128)
        n >>= 7
    result.append(n)
    return result


def generate(source, output):
    if sha256(source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Expected Unicode 17.0.0 Unihan.zip')
    readings = defaultdict(set)
    variants = {}
    with zipfile.ZipFile(source) as archive:
        for cp, field, value in records(archive, 'Readings'):
            if field in ('kMandarin', 'kHanyuPinyin', 'kXHC1983'):
                for group in value.split():
                    for reading in group.split(':')[-1].split(','):
                        readings[cp].add(syllable(reading))
        for cp, field, value in records(archive, 'Variants'):
            if field == 'kSimplifiedVariant' and len(value.split()) == 1:
                simplified = int(value[2:], 16)
                if simplified != cp:
                    variants[cp] = simplified
        for cp, field, value in records(archive, 'IRGSources'):
            if field == 'kCompatibilityVariant':
                target = int(value[2:], 16)
                if target in readings:
                    readings[cp].update(readings[target])
                variants[cp] = variants.get(target, target)
    for line in (HERE / 'mc_rare_overrides.tsv').read_text(encoding='utf8').splitlines():
        if line and not line.startswith('#'):
            char, values = line.split('\t')
            readings[ord(char)].update(map(syllable, values.split()))
    sounds = sorted(set.union(*readings.values()))
    ids = {sound: i for i, sound in enumerate(sounds, 1)}
    pool, groups, codes = [], {}, []
    for cp, values in sorted(readings.items()):
        group = tuple(sorted(ids[v] for v in values))
        if len(group) == 1:
            codes.append(group[0])
        else:
            if group not in groups:
                groups[group] = len(pool)
                pool.extend((len(group), *group))
            codes.append(0x8000 | groups[group])
    assert len(pool) < 32768
    data = bytearray(b'JYP1')
    data.extend(struct.pack('>4I', len(sounds), len(pool), len(codes), len(variants)))
    for sound in sounds:
        data.append(len(sound))
        data.extend(sound.encode('ascii'))
    data.extend(struct.pack('>' + 'H' * len(pool), *pool))
    previous = 0
    for cp in sorted(readings):
        data.extend(varint(cp - previous))
        previous = cp
    data.extend(code & 255 for code in codes)
    data.extend(code >> 8 for code in codes)
    previous = 0
    for cp, target in sorted(variants.items()):
        data.extend(varint(cp - previous))
        distance = target - cp
        data.extend(varint((distance << 1) ^ (distance >> 31)))
        previous = cp
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    print(len(codes), 'characters,', len(sounds), 'syllables,', len(variants), 'variant pairs')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    generate(args.source, args.output)
