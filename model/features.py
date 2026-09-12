"""
Canonical feature extraction for ShortsSense.

IMPORTANT: this file is one half of a contract. The other half is
`android/app/src/main/java/com/shortsense/nlp/Features.kt`. Both must produce
byte-identical feature lists for the same (title, channel) input. That is enforced
by `model/train.py --parity-cases N` (writes `parity.txt`) plus the Kotlin
`ParityTest`, which CI runs on every push.

Spec
----
normalize(s):
  * lowercase
  * keep characters whose Unicode category starts with L (letters), M (marks) or
    N (numbers) -- this keeps Devanagari matras and digits intact
  * everything else (spaces, punctuation, emoji, '#', '@', '_') becomes a space
  * collapse runs of spaces, trim

stem(w):      light, deliberately crude suffix stripper, identical in both languages
              "solved"->"solv", "studying"->"studi", "studies"->"study", "notes"->"note"

features(title, channel):
  * "w:X"     each distinct token of the normalised title / channel, length >= 2
  * "st:X"    the stem of each such token (generalises across word forms)
  * "b:A_B"   each adjacent token pair in the title
  * "c3:XYZ" / "c4:XYZ"  substrings inside each title token
  * "p2:XY"   first 2 chars of each title token
  * "s3:XYZ"  last 3 chars of each title token (token length > 3)
  * "k:i:X"   token stem found in the informative keyword lexicon
  * "k:e:X"   token stem found in the entertainment keyword lexicon
  * "ph:i:how_to"  informative multi-word phrase present in the normalised title
  * "ph:e:wait_for_it"  entertainment multi-word phrase present
  * "ch:i:X" / "ch:e:X"  channel token looks like an education / entertainment brand
  * "__empty"    title has no usable tokens
  * "__nochan"   channel has no usable tokens
  * "__nonascii" raw title contains a non-ASCII character

The keyword lexicon lives in `keywords.py` and is dumped to
`android/app/src/main/assets/keywords.txt`, which the Kotlin side reads at startup.
"""

import unicodedata

import keywords as kw

KEEP_CATEGORIES = ("L", "M", "N")

STEM_SUFFIXES = ("ingly", "edly", "ies", "ied", "ing", "ed", "ly", "es")


def normalize(text: str) -> str:
    if not text:
        return ""
    out = []
    for ch in text.lower():
        if unicodedata.category(ch)[0] in KEEP_CATEGORIES:
            out.append(ch)
        else:
            out.append(" ")
    return " ".join("".join(out).split())


def stem(word: str) -> str:
    if len(word) > 5:
        for suf in STEM_SUFFIXES:
            if word.endswith(suf) and len(word) - len(suf) >= 3:
                base = word[:-len(suf)]
                if suf in ("ies", "ied"):
                    base += "y"
                return base
    if len(word) > 4 and word.endswith("s") and not word.endswith("ss"):
        return word[:-1]
    return word


# built once at import: stem -> kind
_INFO_STEMS = set(stem(w) for w in kw.INFO_KEYWORDS)
_ENT_STEMS = set(stem(w) for w in kw.ENT_KEYWORDS)
_CHAN_INFO = set(stem(w) for w in kw.EDU_CHANNEL_WORDS)
_CHAN_ENT = set(stem(w) for w in kw.ENT_CHANNEL_WORDS)
_INFO_PHRASES = tuple(sorted(kw.INFO_PHRASES))
_ENT_PHRASES = tuple(sorted(kw.ENT_PHRASES))


def _tokens(normalized: str):
    return normalized.split()


def features(title: str, channel: str = None):
    channel = channel or ""
    tnorm = normalize(title)
    cnorm = normalize(channel)
    ttok = _tokens(tnorm)
    ctok = _tokens(cnorm)

    feat = set()

    for w in ttok + ctok:
        if len(w) >= 2:
            feat.add("w:" + w)
            s = stem(w)
            if len(s) >= 2:
                feat.add("st:" + s)
                if s in _INFO_STEMS:
                    feat.add("k:i:" + s)
                elif s in _ENT_STEMS:
                    feat.add("k:e:" + s)

    # channel *type* signal: works for channels the model has never seen
    for w in ctok:
        s = stem(w)
        if s in _CHAN_INFO:
            feat.add("ch:i:" + s)
        elif s in _CHAN_ENT:
            feat.add("ch:e:" + s)

    for a, b in zip(ttok, ttok[1:]):
        feat.add("b:" + a + "_" + b)

    for w in ttok:
        for n in (3, 4):
            if len(w) >= n:
                for i in range(len(w) - n + 1):
                    feat.add("c%d:%s" % (n, w[i:i + n]))
        if len(w) >= 2:
            feat.add("p2:" + w[:2])
        if len(w) > 3:
            feat.add("s3:" + w[-3:])

    padded = " " + tnorm + " " if tnorm else ""
    if padded:
        for p in _INFO_PHRASES:
            if (" " + p + " ") in padded:
                feat.add("ph:i:" + p.replace(" ", "_"))
        for p in _ENT_PHRASES:
            if (" " + p + " ") in padded:
                feat.add("ph:e:" + p.replace(" ", "_"))

    if not ttok:
        feat.add("__empty")
    if not ctok:
        feat.add("__nochan")
    if any(ord(c) > 127 for c in title or ""):
        feat.add("__nonascii")

    return sorted(feat)


def _selfcheck():
    bad = []
    samples = [
        ("Newton's laws in 60 seconds #physics #JEE", "Physics Wallah"),
        ("गुरुत्वाकर्षण बल क्या है? 🔥🔥", "फिजिक्स वाला"),
        ("", ""),
        ("😂😂", ""),
        ("1v4 clutch!!", "BGMI_Highlights"),
        ("how to solve step by step — wait for it", "X"),
    ]
    for t, c in samples:
        for f in features(t, c):
            if "," in f or "\t" in f or "\n" in f:
                bad.append(f)
    assert not bad, "feature keys must not contain , \\t or \\n: %r" % bad[:5]
    return samples


if __name__ == "__main__":
    for t, c in _selfcheck():
        print(repr(t), "|", repr(c), "->", features(t, c)[:8], "...")
    print("stem checks:", [(w, stem(w)) for w in
                           ("solved", "solving", "studies", "notes", "explained",
                            "explains", "revision", "vlogs", "trending", "physics")])
