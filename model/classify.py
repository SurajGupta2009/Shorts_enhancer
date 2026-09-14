#!/usr/bin/env python3
"""
Try the model on a title and channel, from the terminal.

This runs the exact model and feature pipeline that ships inside the app, so it is the
fastest way to answer "would ShortsSense have blocked this?" and to sanity-check a change
to the keyword lexicon.

    python3 model/classify.py "Krebs cycle explained in 60 seconds" "NEETprep"
    python3 model/classify.py --mode study "how mutual funds charge you fees"
    python3 model/classify.py                      # interactive, one per line
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import features as feat  # noqa: E402
import train as T  # noqa: E402


def load(model_dir):
    qw, qb, index, scale = T.read_bin(os.path.join(model_dir, "model.bin"))
    with open(os.path.join(model_dir, "model.json")) as fh:
        meta = json.load(fh)
    return qw, qb, index, meta


def explain(qw, index, fkeys, study_only, limit=5):
    rows = []
    for k in fkeys:
        i = index.get(k)
        if i is None:
            continue
        w = qw[i]
        if study_only:
            d = w[0] - T.logsumexp(w[1], w[2])
        else:
            d = T.logsumexp(w[0], w[1]) - w[2]
        rows.append((d / T.SCALE, k))
    rows.sort(key=lambda r: -abs(r[0]))
    return rows[:limit]


def decide(qw, qb, index, meta, title, channel, mode):
    fkeys = feat.features(title, channel)
    probs = T.predict_q(qw, qb, index, fkeys)
    logits = T.logits_of(qw, qb, index, fkeys)
    mm = T.margin(logits, "info" if mode == "informative" else "study")
    theta = meta["thresholds"]["info_margin" if mode == "informative" else "study_margin"]
    keep = mm >= theta
    print("title   : %s" % (title or "(nothing read)"))
    print("channel : %s" % (channel or "(none)"))
    print("features: %d" % len(fkeys))
    print("p(study)=%.3f  p(info)=%.3f  p(ent)=%.3f" % tuple(probs))
    print("margin  : %.2f   threshold: %.2f" % (mm, theta))
    if not feat.normalize(title):
        print("verdict : KEEP (nothing readable — never block a blank read)")
    else:
        print("verdict : %s" % ("KEEP" if keep else "BLOCK"))
    print("why     : " + ", ".join("%s(%+.2f)" % (k, d) for d, k in explain(
        qw, index, fkeys, mode == "study")))
    print()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("title", nargs="?")
    ap.add_argument("channel", nargs="?", default="")
    ap.add_argument("--mode", default="informative", choices=["informative", "study"])
    ap.add_argument("--model-dir", default=os.path.dirname(os.path.abspath(__file__)))
    args = ap.parse_args()

    qw, qb, index, meta = load(args.model_dir)
    print("model: %d features, trained %s, hard-case accuracy %.1f%%\n"
          % (meta["features"], meta["trained_at"], 100 * meta["metrics"]["hard_cases"]["accuracy"]))

    if args.title is not None:
        title, _, inline_channel = args.title.partition(" | ")
        decide(qw, qb, index, meta, title, inline_channel or args.channel, args.mode)
        return
    print("Type a title, optionally followed by ' | ' and the channel. Ctrl-D to stop.")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        title, _, channel = line.partition(" | ")
        decide(qw, qb, index, meta, title, channel, args.mode)


if __name__ == "__main__":
    main()
