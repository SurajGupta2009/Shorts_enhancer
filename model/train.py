"""
ShortsSense trainer.

Trains a 3-class linear softmax ("study" / "info" / "ent") on sparse lexical features and
distils it into an int16 lookup table the Android app evaluates in well under a
millisecond with a single hash-map pass. No runtime ML framework on the phone.

Decisions on the phone use a *margin* (log-odds) rather than a raw probability:
    margin_info  = log P(study or info) - log P(ent)
    margin_study = log P(study)        - log P(info or ent)
Keeping the two error types (blocking something useful / letting junk through) in a
trade-off the user can move with one slider, and making the threshold meaningful even
when the model is as confident as this one is on synthetic data.

Outputs (next to this file unless --out is given):
  model.bin     int16 lookup table loaded by the app from assets
  model.json    metadata, thresholds, metrics - read by the app and by CI
  REPORT.md     human readable metrics and the full trade-off table
  parity.txt    fixture proving Kotlin and Python compute identical numbers

Pure standard library on purpose: `python3 model/train.py` reproduces the shipped model
on any machine with no pip install.
"""

import argparse
import json
import math
import os
import random
import struct
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import calibration  # noqa: E402
import dataset  # noqa: E402
import features as feat  # noqa: E402
import hard_cases  # noqa: E402

CLASSES = ["study", "info", "ent"]  # index 0, 1, 2

# Which generated domains count as academic study (class 0) as opposed to general
# knowledge or a practical skill (class 1). Getting this wrong makes the "Study only"
# mode block physics lectures, which is exactly the kind of bug the user would feel.
ACADEMIC_DOMAINS = {"science", "math", "bio", "cs", "exam", "study", "lang", "med"}
MAGIC = b"SSM1"
FORMAT_VERSION = 1
SCALE = 4096


# --------------------------------------------------------------------------------
# data
# --------------------------------------------------------------------------------
def load_rows(rows):
    X, y = [], []
    for r in rows:
        f = feat.features(r["title"], r["channel"])
        if not f:
            continue
        X.append(f)
        if r["domain"] == "ent":
            y.append(2)
        elif r["domain"] in ACADEMIC_DOMAINS:
            y.append(0)
        else:  # "info" and "skill": useful knowledge, but not exam study
            y.append(1)
    return X, y


def build_vocab(all_features, max_features):
    df = {}
    for f in all_features:
        for k in f:
            df[k] = df.get(k, 0) + 1
    items = [(k, c) for k, c in df.items() if c >= 3 and k not in ("__empty", "__nochan")]
    items.sort(key=lambda kv: (-kv[1], kv[0]))
    items = items[:max_features]
    return {k: i for i, (k, _) in enumerate(items)}


# --------------------------------------------------------------------------------
# training
# --------------------------------------------------------------------------------
def train(X, y, index, epochs, lr, l2, class_weights, seed, avg="per_feature", verbose=True):
    rng = random.Random(seed)
    nf = len(index)
    W = [[0.0, 0.0, 0.0] for _ in range(nf)]
    G = [[0.0, 0.0, 0.0] for _ in range(nf)]
    CNT = [0] * nf
    B = [0.0, 0.0, 0.0]
    GB = [0.0, 0.0, 0.0]
    Bsum = [0.0, 0.0, 0.0]
    Wsum = [[0.0, 0.0, 0.0] for _ in range(nf)]
    steps = 0

    order = list(range(len(X)))
    for ep in range(epochs):
        rng.shuffle(order)
        t0 = time.time()
        loss_sum = 0.0
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
            loss_sum += -math.log(max(p[yi], 1e-12))
            cw = class_weights[yi]
            g = [cw * (p[c] - (1.0 if c == yi else 0.0)) for c in range(3)]
            for c in range(3):
                GB[c] += g[c] * g[c]
                B[c] -= lr * g[c] / (math.sqrt(GB[c]) + 1e-8)
                Bsum[c] += B[c]
            steps += 1
            for j in idxs:
                w = W[j]
                acc = G[j]
                for c in range(3):
                    gc = g[c] + l2 * w[c]
                    acc[c] += gc * gc
                    w[c] -= lr * gc / (math.sqrt(acc[c]) + 1e-8)
                    Wsum[j][c] += w[c]
                CNT[j] += 1
        if verbose:
            print("  epoch %d/%d  loss=%.4f  (%.1fs)" % (ep + 1, epochs, loss_sum / len(order),
                                                         time.time() - t0), flush=True)

    if avg == "per_feature":
        # average each parameter over the steps where it was actually updated: a rare but
        # decisive feature ("arrhenius") keeps its weight instead of being diluted by the
        # 30k steps where it was absent. Worth ~15 accuracy points on the hard cases.
        Wavg = [[(Wsum[j][c] / CNT[j]) if CNT[j] else 0.0 for c in range(3)] for j in range(nf)]
    else:
        Wavg = [[c / steps for c in row] for row in Wsum]
    Bavg = [c / steps for c in Bsum]
    return Wavg, Bavg


def predict(W, B, index, fkeys):
    logits = [B[0], B[1], B[2]]
    for k in fkeys:
        j = index.get(k)
        if j is None:
            continue
        w = W[j]
        logits[0] += w[0]
        logits[1] += w[1]
        logits[2] += w[2]
    m = max(logits)
    ex = [math.exp(logits[0] - m), math.exp(logits[1] - m), math.exp(logits[2] - m)]
    s = ex[0] + ex[1] + ex[2]
    return [ex[0] / s, ex[1] / s, ex[2] / s]


# --------------------------------------------------------------------------------
# quantisation: int16 table, integer accumulation, identical softmax on device
# --------------------------------------------------------------------------------
def quantise(W, B):
    QW = []
    for row in W:
        q = []
        for c in row:
            q.append(max(-32768, min(32767, int(round(c * SCALE)))))
        QW.append(q)
    return QW, [max(-2 ** 31, min(2 ** 31 - 1, int(round(b * SCALE)))) for b in B]


def predict_q(QW, QB, index, fkeys):
    logits = [QB[0], QB[1], QB[2]]
    for k in fkeys:
        j = index.get(k)
        if j is None:
            continue
        w = QW[j]
        logits[0] += w[0]
        logits[1] += w[1]
        logits[2] += w[2]
    f = [l / SCALE for l in logits]
    m = max(f)
    ex = [math.exp(f[0] - m), math.exp(f[1] - m), math.exp(f[2] - m)]
    s = ex[0] + ex[1] + ex[2]
    return [ex[0] / s, ex[1] / s, ex[2] / s]


def logsumexp(a, b):
    m = a if a > b else b
    return m + math.log(math.exp(a - m) + math.exp(b - m))


def logits_of(QW, QB, index, fkeys):
    """Scaled logits, byte for byte the same integers the phone accumulates."""
    out = [QB[0], QB[1], QB[2]]
    for k in fkeys:
        j = index.get(k)
        if j is None:
            continue
        w = QW[j]
        out[0] += w[0]
        out[1] += w[1]
        out[2] += w[2]
    return [x / SCALE for x in out]


def margin(logits, mode):
    """log-odds in favour of keeping: the quantity both the app and these metrics use.

    Computed with log-sum-exp on the logits rather than from probabilities. Doing it via
    probabilities needs an epsilon guard, and that guard silently caps the value at about
    +/-27.6 - which would make the shipped thresholds describe a different quantity than
    the app thresholds on.
    """
    if mode == "info":
        return logsumexp(logits[0], logits[1]) - logits[2]
    return logits[0] - logsumexp(logits[1], logits[2])


def write_bin(path, QW, QB, index):
    keys = [None] * len(index)
    for k, i in index.items():
        keys[i] = k
    with open(path, "wb") as fh:
        fh.write(MAGIC)
        fh.write(struct.pack("<H", FORMAT_VERSION))
        fh.write(struct.pack("<H", len(CLASSES)))
        fh.write(struct.pack("<iii", QB[0], QB[1], QB[2]))
        fh.write(struct.pack("<i", SCALE))
        fh.write(struct.pack("<I", len(keys)))
        for i, k in enumerate(keys):
            kb = k.encode("utf-8")
            assert len(kb) < 256, k
            fh.write(struct.pack("<B", len(kb)))
            fh.write(kb)
            w = QW[i]
            fh.write(struct.pack("<hhh", w[0], w[1], w[2]))


def read_bin(path):
    with open(path, "rb") as fh:
        blob = fh.read()
    assert blob[:4] == MAGIC, "bad magic"
    off = 4
    ver, ncls = struct.unpack_from("<HH", blob, off)
    off += 4
    assert ver == FORMAT_VERSION and ncls == 3
    qb = list(struct.unpack_from("<iii", blob, off))
    off += 12
    scale = struct.unpack_from("<i", blob, off)[0]
    off += 4
    n = struct.unpack_from("<I", blob, off)[0]
    off += 4
    index, qw = {}, []
    for i in range(n):
        ln = blob[off]
        off += 1
        key = blob[off:off + ln].decode("utf-8")
        off += ln
        qw.append(list(struct.unpack_from("<hhh", blob, off)))
        off += 6
        index[key] = i
    return qw, qb, index, scale


# --------------------------------------------------------------------------------
# evaluation
# --------------------------------------------------------------------------------
def rates(margins, labels, theta, mode):
    """(useful content blocked, junk let through) at margin threshold theta."""
    good_blocked = good_total = junk_kept = junk_total = 0
    for mm, yi in zip(margins, labels):
        keep = mm >= theta
        want_keep = (yi != 2) if mode == "info" else (yi == 0)
        if want_keep:
            good_total += 1
            if not keep:
                good_blocked += 1
        else:
            junk_total += 1
            if keep:
                junk_kept += 1
    return good_blocked / max(1, good_total), junk_kept / max(1, junk_total)


def quantile_threshold(margins, budget):
    """Margin below which `budget` of the useful examples fall.

    Probabilities from this model saturate (it is extremely confident on synthetic data),
    so thresholding a probability or sweeping a fixed grid is misleading. Defining the
    threshold as a quantile of the useful-content margin distribution gives it a meaning
    the user can actually feel: "block at most this share of things I wanted to watch".
    """
    ms = sorted(margins)
    if not ms:
        return 0.0
    k = budget * (len(ms) - 1)
    lo, hi = int(math.floor(k)), int(math.ceil(k))
    if lo == hi:
        return ms[lo]
    return ms[lo] + (ms[hi] - ms[lo]) * (k - lo)


def fit_temperature(probs, labels):
    """One-parameter post-hoc calibration, used only for the number shown in the UI and
    for the confidence gate. Decisions are margin based and unaffected."""
    best_t, best_nll = 1.0, None
    for t in [x / 10.0 for x in range(5, 60)]:
        nll = 0.0
        for p, yi in zip(probs, labels):
            logs = [math.log(max(p[c], 1e-12)) / t for c in range(3)]
            m = max(logs)
            ex = [math.exp(l - m) for l in logs]
            s = sum(ex)
            nll += -math.log(max(ex[yi] / s, 1e-12))
        if best_nll is None or nll < best_nll:
            best_nll, best_t = nll, t
    return best_t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--epochs", type=int, default=6)
    ap.add_argument("--lr", type=float, default=0.6)
    ap.add_argument("--l2", type=float, default=2e-6)
    ap.add_argument("--max-features", type=int, default=20000)
    ap.add_argument("--seed", type=int, default=20260912)
    # 36000 rather than 26000: the corpus now covers several extra families (PCM practice,
    # motivation, civics, history, ideas, adult clickbait), and thinning the older families
    # to pay for them measurably hurt the borderline cases.
    ap.add_argument("--train-size", type=int, default=36000)
    ap.add_argument("--parity-cases", type=int, default=150)
    ap.add_argument("--avg", default="per_feature", choices=["per_feature", "global"])
    ap.add_argument("--good-budget", type=float, default=0.04,
                    help="share of useful content the default thresholds may block")
    args = ap.parse_args()

    t_start = time.time()
    print("== building corpus ==", flush=True)
    rows = dataset.build(random.Random(7), n_target=args.train_size)
    X, y = load_rows(rows)
    test_rows = dataset.build(random.Random(11), n_target=3500, holdout=True)
    Xt, yt = load_rows(test_rows)
    print("   train docs: %d   unseen-topic test docs: %d" % (len(X), len(Xt)))

    index = build_vocab(X, args.max_features)
    print("   vocabulary: %d" % len(index))

    print("== training (avg=%s, %d epochs) ==" % (args.avg, args.epochs), flush=True)
    class_weights = [1.15, 1.10, 1.0]  # blocking something useful is the costly error
    W, B = train(X, y, index, args.epochs, args.lr, args.l2, class_weights, args.seed, args.avg)
    QW, QB = quantise(W, B)

    qerr = 0.0
    for i in range(min(500, len(X))):
        p1, p2 = predict(W, B, index, X[i]), predict_q(QW, QB, index, X[i])
        qerr = max(qerr, max(abs(a - b) for a, b in zip(p1, p2)))
    print("   max quantisation error on probabilities: %.5f" % qerr)

    probs_t = [predict_q(QW, QB, index, f) for f in Xt]
    logits_t = [logits_of(QW, QB, index, f) for f in Xt]
    margins_info_t = [margin(l, "info") for l in logits_t]
    margins_study_t = [margin(l, "study") for l in logits_t]
    gb_info, jk_info = rates(margins_info_t, yt, 0.0, "info")
    gb_study, jk_study = rates(margins_study_t, yt, 0.0, "study")
    temperature = fit_temperature(probs_t, yt)

    # thresholds are chosen on the calibration set, never on the reporting set
    cal = calibration.CALIBRATION_CASES
    CX = [feat.features(c["title"], c["channel"]) for c in cal]
    CP = [predict_q(QW, QB, index, f) for f in CX]
    CL = [logits_of(QW, QB, index, f) for f in CX]
    c_info_labels = [0 if c["informative"] else 2 for c in cal]
    c_study_labels = [0 if c["study"] else 2 for c in cal]
    info_margins = [margin(l, "info") for l, c in zip(CL, cal) if c["informative"]]
    study_margins = [margin(l, "study") for l, c in zip(CL, cal) if c["study"]]
    theta_info = quantile_threshold(info_margins, args.good_budget)
    theta_study = quantile_threshold(study_margins, args.good_budget)
    c_gb, c_jk = rates([margin(l, "info") for l in CL], c_info_labels, theta_info, "info")
    c_sgb, c_sjk = rates([margin(l, "study") for l in CL], c_study_labels, theta_study, "study")
    # preset rows the app turns into a strictness slider
    presets = {
        "informative": [{"budget": b, "margin": round(quantile_threshold(info_margins, b), 3)}
                        for b in (0.01, 0.02, 0.05, 0.10, 0.20)],
        "study": [{"budget": b, "margin": round(quantile_threshold(study_margins, b), 3)}
                  for b in (0.01, 0.02, 0.05, 0.10, 0.20)],
    }
    print("   calibration set: info margin=%.2f -> useful-blocked=%.1f%% junk-kept=%.1f%%"
          % (theta_info, 100 * c_gb, 100 * c_jk))
    print("   calibration set: study margin=%.2f -> study-blocked=%.1f%% rest-kept=%.1f%%"
          % (theta_study, 100 * c_sgb, 100 * c_sjk))
    print("   synthetic: info margin=%.2f -> useful-blocked=%.1f%% junk-kept=%.1f%%"
          % (theta_info, 100 * gb_info, 100 * jk_info))
    print("   synthetic: study margin=%.2f -> useful-blocked=%.1f%% junk-kept=%.1f%%"
          % (theta_study, 100 * gb_study, 100 * jk_study))
    print("   display temperature: %.1f" % temperature)

    # hard cases: hand written, never trained on
    hc = hard_cases.HARD_CASES
    HX = [feat.features(c["title"], c["channel"]) for c in hc]
    HP = [predict_q(QW, QB, index, f) for f in HX]
    HL = [logits_of(QW, QB, index, f) for f in HX]
    errors = []
    for c, p, l in zip(hc, HP, HL):
        keep = margin(l, "info") >= theta_info
        want = bool(c["informative"])
        keep_want = p[0] + p[1] >= 0.5
        if keep != want:
            errors.append((c, p, want, keep))
    n_good = sum(1 for c in hc if c["informative"])
    n_junk = len(hc) - n_good
    hc_acc = 1 - len(errors) / len(hc)
    hc_good_blocked = sum(1 for c, p, w, k in errors if w) / n_good
    hc_junk_kept = sum(1 for c, p, w, k in errors if not w) / n_junk
    print("   hard cases: accuracy=%.1f%%  useful-blocked=%.1f%%  junk-kept=%.1f%%  (%d wrong of %d)"
          % (100 * hc_acc, 100 * hc_good_blocked, 100 * hc_junk_kept, len(errors), len(hc)))

    # trade-off table on hard cases, so the user can see what the slider does
    curve = []
    hc_info_labels = [0 if c["informative"] else 2 for c in hc]
    hc_margins = [margin(l, "info") for l in HL]
    for theta in [-20.0, -10.0, -5.0, -2.0, -1.0, 0.0, 1.0, 2.0, 5.0, 10.0]:
        gb, jk = rates(hc_margins, hc_info_labels, theta, "info")
        curve.append((theta, gb, jk))

    os.makedirs(args.out, exist_ok=True)
    bin_path = os.path.join(args.out, "model.bin")
    write_bin(bin_path, QW, QB, index)
    qw2, qb2, index2, scale2 = read_bin(bin_path)
    assert scale2 == SCALE and len(index2) == len(index)
    for i in range(min(200, len(X))):
        a, b = predict_q(QW, QB, index, X[i]), predict_q(qw2, qb2, index2, X[i])
        assert max(abs(x - z) for x, z in zip(a, b)) < 1e-9, "round trip mismatch"
    print("   wrote %s (%.1f KB)" % (bin_path, os.path.getsize(bin_path) / 1024.0))

    meta = {
        "name": "ShortsSense tiny text model",
        "version": FORMAT_VERSION,
        "classes": CLASSES,
        "features": len(index),
        "scale": SCALE,
        "thresholds": {
            "info_margin": round(theta_info, 3),
            "study_margin": round(theta_study, 3),
            "temperature": round(temperature, 2),
            "presets": presets,
        },
        "metrics": {
            "synthetic_unseen_topics": {
                "info_mode": {"theta": theta_info, "useful_blocked": round(gb_info, 4),
                              "junk_kept": round(jk_info, 4)},
                "study_mode": {"theta": theta_study, "useful_blocked": round(gb_study, 4),
                               "junk_kept": round(jk_study, 4)},
            },
            "hard_cases": {"n": len(hc), "accuracy": round(hc_acc, 4),
                           "useful_blocked": round(hc_good_blocked, 4),
                           "junk_kept": round(hc_junk_kept, 4)},
            "max_quantisation_error": round(qerr, 6),
        },
        "trained_at": time.strftime("%Y-%m-%d", time.gmtime()),
        "train_docs": len(X),
        "seed": args.seed,
    }
    with open(os.path.join(args.out, "model.json"), "w") as fh:
        json.dump(meta, fh, indent=2)

    # flat metadata file: the Android app reads this, and so does the Kotlin unit test.
    # Deliberately not JSON so it needs no parser on either side.
    meta_lines = [
        "# ShortsSense model metadata - generated by model/train.py, do not edit by hand",
        "version=%d" % FORMAT_VERSION,
        "classes=%s" % ",".join(CLASSES),
        "features=%d" % len(index),
        "scale=%d" % SCALE,
        "trained_at=%s" % meta["trained_at"],
        "seed=%d" % args.seed,
        "train_docs=%d" % len(X),
        "info_margin=%.4f" % theta_info,
        "study_margin=%.4f" % theta_study,
        "temperature=%.2f" % temperature,
        "presets_informative=" + ",".join("%.2f:%.4f" % (p["budget"], p["margin"])
                                          for p in presets["informative"]),
        "presets_study=" + ",".join("%.2f:%.4f" % (p["budget"], p["margin"])
                                    for p in presets["study"]),
        "hard_cases=%d" % len(hc),
        "hard_accuracy=%.4f" % hc_acc,
        "hard_useful_blocked=%.4f" % hc_good_blocked,
        "hard_junk_kept=%.4f" % hc_junk_kept,
        "synthetic_useful_blocked=%.4f" % gb_info,
        "synthetic_junk_kept=%.4f" % jk_info,
        "quantisation_error=%.6f" % qerr,
    ]
    with open(os.path.join(args.out, "model.meta"), "w") as fh:
        fh.write("\n".join(meta_lines) + "\n")
    print("   wrote model.meta")

    # parity fixture for the Kotlin tests
    import base64
    parity_samples = []
    rng = random.Random(99)
    for i in rng.sample(range(len(X)), min(args.parity_cases, len(X))):
        parity_samples.append((rows[i]["title"], rows[i]["channel"]))
    parity_samples += [(c["title"], c["channel"]) for c in hc]
    parity_samples += [
        ("", ""), ("😂😂😂", ""),
        ("गुरुत्वाकर्षण बल क्या है?", "फिजिक्स वाला"),
        ("Newton's 2nd law!! #JEE_Mains (2026)", "PW — Physics Wallah"),
        ("A" * 300, "x"),
        ("emoji test 🚀🧪 and #hash @handle", "Channel_Name 2.0"),
        ("studies studying studied solves solving", "Notes"),
    ]
    parity_path = os.path.join(args.out, "parity.txt")
    with open(parity_path, "w", encoding="utf-8") as fh:
        fh.write("# ShortsSense parity fixture v1\n")
        fh.write("# base64(title)\tbase64(channel)\tfeatures\tstudy,info,ent (probabilities x1e6)"
                 "\tmargin_info\tmargin_study\n")
        for title, channel in parity_samples:
            f = feat.features(title, channel)
            p = predict_q(QW, QB, index, f)
            # margins are written explicitly: a probability rounded to six decimals
            # cannot represent them (the model saturates at ~1e-12), and the margin is
            # the quantity the app actually thresholds on.
            lg = logits_of(QW, QB, index, f)
            m_info = margin(lg, "info")
            m_study = margin(lg, "study")
            fh.write("%s\t%s\t%s\t%s\t%.6f\t%.6f\n" % (
                base64.b64encode(title.encode("utf-8")).decode("ascii"),
                base64.b64encode(channel.encode("utf-8")).decode("ascii"),
                ",".join(f),
                ",".join(str(int(round(x * 1000000))) for x in p),
                m_info, m_study))
    print("   wrote %s (%d cases)" % (parity_path, len(parity_samples)))

    report = [
        "# ShortsSense model report",
        "",
        "Generated by `model/train.py` on %s (seed %d, avg=%s, %d epochs)."
        % (meta["trained_at"], args.seed, args.avg, args.epochs),
        "",
        "## What this is",
        "",
        "A 3-class linear softmax (`study` / `info` / `ent`) over sparse lexical features,",
        "trained on a synthetic corpus and quantised to an int16 lookup table",
        "(`model.bin`, %.1f KB, %d features). The phone does one hash-map pass and a"
        % (os.path.getsize(bin_path) / 1024.0, len(index)),
        "softmax per decision: no runtime ML framework, no internet, no GPU, and no",
        "latency you can feel.",
        "",
        "## Metrics",
        "",
        "| setting | threshold (margin) | useful content blocked | junk let through |",
        "|---|---|---|---|",
        "| unseen topics, informative mode | %.2f | %.1f%% | %.1f%% |"
        % (theta_info, 100 * gb_info, 100 * jk_info),
        "| unseen topics, study-only mode | %.2f | %.1f%% | %.1f%% |"
        % (theta_study, 100 * gb_study, 100 * jk_study),
        "| hand-written hard cases | %.2f | %.1f%% | %.1f%% |"
        % (theta_info, 100 * hc_good_blocked, 100 * hc_junk_kept),
        "",
        "Hard-case accuracy: **%.1f%%** (%d of %d borderline titles correct)."
        % (100 * hc_acc, len(hc) - len(errors), len(hc)),
        "",
        "The `unseen topics` rows come from held-out content topics but the same phrasing",
        "templates as training, so read them as an upper bound. The hard-case row is",
        "hand-written prose the model has never seen: trust that one more.",
        "",
        "Max quantisation error on probabilities: %.5f." % qerr,
        "Display temperature for the UI confidence number: %.1f (decisions do not use it).",
        "",
        "## The trade-off the slider moves",
        "",
        "Margins are log-odds in favour of keeping a Short. Higher = stricter.",
        "",
        "| margin | useful content blocked | junk let through |",
        "|---|---|---|",
    ]
    report += ["| %.2f | %.1f%% | %.1f%% |" % (t, 100 * gb, 100 * jk) for t, gb, jk in curve]
    report += [
        "",
        "## Mistakes on the hard cases (the honest list)",
        "",
    ]
    if errors:
        report.append("| title | channel | model said | truth | P(info) |")
        report.append("|---|---|---|---|---|")
        for c, p, want, keep in errors:
            report.append("| %s | %s | %s | %s | %.2f |" % (
                c["title"].replace("|", "/"), c["channel"].replace("|", "/"),
                "keep" if keep else "block", "informative" if want else "entertainment",
                p[0] + p[1]))
    else:
        report.append("None - every hard case was classified correctly.")
    report += [
        "",
        "## Reproduce",
        "",
        "```bash",
        "python3 model/train.py          # retrain from scratch, ~30 s, stdlib only",
        "python3 model/tune.py 20000     # sweep the training knobs, ~3 min",
        "```",
        "",
        "## Files",
        "",
        "| file | what |",
        "|---|---|",
        "| `model.bin` | int16 table the app loads from assets |",
        "| `model.json` | thresholds + metrics the app reads for defaults |",
        "| `parity.txt` | fixture proving Kotlin == Python inference |",
        "| `dataset.py` | synthetic corpus generator (incl. Devanagari slice) |",
        "| `hard_cases.py` | %d hand-labelled borderline titles |" % len(hc),
        "| `features.py` | canonical feature spec (mirrored in Kotlin) |",
        "| `keywords.py` | style lexicon dumped to the app's assets |",
        "",
    ]
    with open(os.path.join(args.out, "REPORT.md"), "w") as fh:
        fh.write("\n".join(report))
    print("   wrote REPORT.md")
    print("== done in %.1fs ==" % (time.time() - t_start))


if __name__ == "__main__":
    main()
