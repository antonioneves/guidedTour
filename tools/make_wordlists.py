"""Generates the word lists that correct misread words, one per language, from wordfreq.

    pip install wordfreq
    python3 tools/make_wordlists.py app/src/main/assets/words

Each list holds the language's most common words (up to 150,000), sorted and front-coded: one line
per word, its first character the number of leading characters it shares with the word before (as
chr(48 + n)), its second its frequency (chr(48 + round(6 * log10(frequency * 1e9)))), then the rest
of the word. The file is gzipped but named .words, since the Android build unzips .gz assets.
The data is from wordfreq by Robyn Speer, under CC BY-SA 4.0.
"""
import gzip
import math
import os
import sys
from pathlib import Path

import regex
import wordfreq

# The languages wordfreq has lists for and the camera can read (Latin letters and Devanagari).
LANGUAGES = "ca cs da de en es fi fil fr hi hu id is it lt lv ms nb nl pl pt ro sh sk sl sv tr vi".split()
MAX_WORDS = 150_000
MAX_SHARED = 60  # chr(48 + 60) is still a plain ASCII character
WORD = regex.compile(r"[\p{L}\p{M}]+")


def frequency_code(frequency):
    return max(0, min(60, round(6 * math.log10(frequency * 1e9))))


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for language in LANGUAGES:
        frequencies = wordfreq.get_frequency_dict(language, "best")
        # Letters only, and only characters Kotlin keeps in one char, so offsets agree.
        words = [w for w in sorted(frequencies, key=frequencies.get, reverse=True)
                 if WORD.fullmatch(w) and all(ord(c) < 0x10000 for c in w)][:MAX_WORDS]
        lines, previous = [], ""
        for word in sorted(words):
            shared = min(len(os.path.commonprefix([previous, word])), MAX_SHARED)
            lines.append(chr(48 + shared) + chr(48 + frequency_code(frequencies[word])) + word[shared:])
            previous = word
        data = gzip.compress("\n".join(lines).encode("utf-8"), 9, mtime=0)
        (out / f"{language}.words").write_bytes(data)
        print(f"{language}: {len(words)} words, {len(data) // 1024} KB")


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/words"))
