"""
Experiment harness: sweep training options and report the metrics that matter.

The number to watch is `hard` (accuracy on hand-written borderline titles the model has
never seen) together with `good-blocked` (share of useful content wrongly blocked).
Run: python3 model/tune.py
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import dataset  # noqa: E402
import features as feat  # noqa: E402
import hard_cases  # noqa: E402
import train as T  # noqa: E402


def build_data(train_size):
    rows = dataset.build(random.Random(7), n_target=train_size)
    X, y = T.load_rows(rows)
    test_rows = dataset.build(random.Random(11), n_target=3500, holdout=True)
    Xt, yt = T.load_rows(test_rows)
    hc = hard_cases.HARD_CASES
    HX = [feat.features(c["title"], c["channel"]) for c in hc]
    return rows, X, y, Xt, yt, hc, HX


def train_variant(X, y, index, opts):
    """Copy of T.train with the extra knobs (kept separate so the shipped trainer stays
    simple). Differences: per-feature averaging, label smoothing, momentum-free."""
    rng = random.Random(opts["seed"])
    nf = len(index)
    W = [[0.0, 0.0, 0.0] for _ in range(nf)]
    G = [[0.0, 0.0, 0.0] for _ in range(nf)]
    CNT = [0] * nf
    B = [0.0, 0.0, 0.0]
    GB = [0.0, 0.0, 0.0]
    BC = 0
    Bsum = [0.0, 0.0, 0.0]
    Wsum = [[0.0, 0.0, 0.0] for _ in range(nf)]
    lr, l2 = opts["lr"], opts["l2"]
    ls = opts["label_smooth"]
    cw = opts["class_weights"]
    order = list(range(len(X)))
    for ep in range(opts["epochs"]):
        rng.shuffle(order)
        for i in order:
            idxs = []
            logits = [B[0], B[1], B[2]]
            for k in X[i]:
                j = index.get(k)
                if j is None:
                    continue
                idxs.append(j)
                w = W[j]
                logits[0] += w[0]
                logits[1] += w[1]
                logits[2] += w[2]
            m = max(logits)
            ex = [math.exp(logits[0] - m), math.exp(logits[1] - m), math.exp(logits[2] - m)]
            s = ex[0] + ex[1] + ex[2]
            p = [ex[0] / s, ex[1] / s, ex[2] / s]
            yi = y[i]
            tgt = [(1 - ls) + ls / 3.0 if yi == c else ls / 3.0 for c in range(3)]
            g = [cw[yi] * (p[c] - tgt[c]) for c in range(3)]
            for c in range(3):
                GB[c] += g[c] * g[c]
                B[c] -= lr * g[c] / (math.sqrt(GB[c]) + 1e-8)
                Bsum[c] += B[c]
            BC += 1
            for j in idxs:
                w = W[j]
                acc = G[j]
                for c in range(3):
                    gc = g[c] + l2 * w[c]
                    acc[c] += gc * gc
                    w[c] -= lr * gc / (math.sqrt(acc[c]) + 1e-8)
                    Wsum[j][c] += w[c]
                CNT[j] += 1
    if opts["avg"] == "per_feature":
        Wavg = [[(Wsum[j][c] / CNT[j]) if CNT[j] else 0.0 for c in range(3)] for j in range(nf)]
    else:
        Wavg = [[c / BC for c in row] for row in Wsum]
    Bavg = [c / BC for c in Bsum]
    return Wavg, Bavg


def evaluate(W, B, index, Xt, yt, hc, HX):
    # Thresholds are quantiles of the useful content's own margins (see train.py); a grid
    # search saturates at the edge of whatever window it is given, which is why it is gone.
    margins_t = [T.margin(T.logits_of(W, B, index, f), "info") for f in Xt]
    tau = T.quantile_threshold([m for m, yi in zip(margins_t, yt) if yi != 2], 0.05)
    gb, jk = T.rates(margins_t, yt, tau, "info")
    hmargins = [T.margin(T.logits_of(W, B, index, f), "info") for f in HX]
    hp = [T.predict(W, B, index, f) for f in HX]
    err = 0
    good_blocked = 0
    junk_kept = 0
    n_good = sum(1 for c in hc if c["informative"])
    n_junk = len(hc) - n_good
    for c, m in zip(hc, hmargins):
        keep = m >= tau
        want = bool(c["informative"])
        if keep == want:
            continue
        err += 1
        if want:
            good_blocked += 1
        else:
            junk_kept += 1
    return {
        "tau": round(tau, 3),
        "syn_good_blocked": round(gb, 4),
        "syn_junk_kept": round(jk, 4),
        "hard_acc": round(1 - err / len(hc), 4),
        "hard_good_blocked": round(good_blocked / n_good, 4),
        "hard_junk_kept": round(junk_kept / n_junk, 4),
    }


def main():
    train_size = int(sys.argv[1]) if len(sys.argv) > 1 else 20000
    rows, X, y, Xt, yt, hc, HX = build_data(train_size)
    index = T.build_vocab(X, 20000)
    print("docs=%d vocab=%d" % (len(X), len(index)), flush=True)
    variants = []
    for avg in ("global", "per_feature"):
        for l2 in (2e-6, 5e-5, 2e-4):
            for ls in (0.0, 0.1):
                variants.append(dict(epochs=5, lr=0.6, l2=l2, label_smooth=ls, avg=avg,
                                     class_weights=[1.15, 1.10, 1.0], seed=5))
    results = []
    for v in variants:
        W, B = train_variant(X, y, index, v)
        m = evaluate(W, B, index, Xt, yt, hc, HX)
        tag = "avg=%-11s l2=%-8g ls=%.1f" % (v["avg"], v["l2"], v["label_smooth"])
        print("%s  hard=%.3f  hgood=%.3f  hjunk=%.3f  tau=%.2f  syn_good=%.3f syn_junk=%.3f"
              % (tag, m["hard_acc"], m["hard_good_blocked"], m["hard_junk_kept"], m["tau"],
                 m["syn_good_blocked"], m["syn_junk_kept"]), flush=True)
        results.append((tag, m))
    results.sort(key=lambda r: (-r[1]["hard_acc"], r[1]["hard_good_blocked"]))
    print("\nbest:", json.dumps(results[0], indent=1))


if __name__ == "__main__":
    main()
