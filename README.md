# ShortsSense

**Keeps the YouTube Shorts that teach you something. Blocks the ones that don't.**

Turning Shorts off entirely is a blunt instrument: you lose the exam revision Short, the
"why is the sky blue" explainer and the guitar tutorial along with the prank videos. This
is an Android app that sits between you and the Shorts feed and makes that call one Short
at a time — locally, in about a millisecond, with no internet connection.

When it decides a Short is not for you, it covers the screen with a short countdown and
shows you exactly why: the title it read, the channel, and the words that tipped the
decision. You can overrule it in one tap, and it remembers.

```
   YouTube Shorts player
            │  accessibility events
            ▼
   is this the Shorts player?          player-only view IDs + retries
            │
            ▼
   what is on screen?                  every text node is scored, best title wins
            │
            ▼
   3-class linear model (study/info/ent)   ~1 ms, no framework, no network
            │
     keep ──┴── block
      │              │
   nothing        block screen: title, channel, reason, countdown,
   happens         "keep watching anyway", "always allow this channel"
```

---

## Install (no Android Studio needed)

1. Open the **Actions** tab of this repository
   ([direct link](https://github.com/SurajGupta2009/Shorts_enhancer/actions/workflows/android.yml))
   and click the newest **Build ShortsSense APK** run — the newest run also prints the APK's
   SHA-256 and the install notes in its summary.
2. Download the **`ShortsSense-apk`** artifact (a zip containing `app-debug.apk` and
   `apk-sha256.txt`), or take the APK from **Releases** if a tagged release exists.
3. On the phone, open the APK and allow "install unknown apps" for whatever app opened it.
4. Open **ShortsSense** and tap **Open accessibility settings**.
5. Find **ShortsSense** in that list, turn it on, and confirm the Android warning.

That is the whole setup. The app asks for exactly one permission.

## Using it

| Control | What it does |
|---|---|
| **What counts as useful** | *Informative* — study and exam problems (P/C/M), motivation that teaches something, science and explainers, new ideas and inventions, history, politics and civics — or *Study only* (academic material; stricter, see the caveat below) |
| **Strictness** | How much useful content you are willing to lose to catch more junk, from *Fewest interruptions* (~1%) to *Maximum filtering* (~20%) |
| **When a Short is blocked** | Either the countdown **skips to the next Short**, or it **leaves Shorts** entirely |
| **Countdown seconds** | 0–10, or off (the block screen then waits for you) |
| **Channels** | Anything you allow-list is never judged again; the app can also learn a channel when you tap *Always allow* |
| **Detection log** | What the app read off the screen, decision by decision — the screen to open when something goes wrong |

Memes, "sigma grindset" hype edits, and racy or adult content are blocked in both modes:
that is what the app is for. A motivational *talk* or lesson is treated as informative,
an "attitude status" edit is not.

**Today** on the home screen counts how many Shorts were blocked and how many were let
through, which is the honest measure of how much feed you actually skipped.

## When it gets something wrong

It will, sometimes. It only sees a title and a channel.

| What happened | What to do |
|---|---|
| A useful Short was covered | Tap **Keep watching anyway** on the block screen. If it is a channel you trust, tap **Always allow this channel** instead. |
| Junk keeps getting through | Raise **Strictness** one step. |
| Too many useful Shorts are being blocked | Lower **Strictness** one step. |
| A channel is never right | Allow-list it (or block-list it from the log screen) and it stops being judged. |
| Nothing is being blocked at all | Open **Detection log → Capture screen** while a Short is playing. If the title reads as empty, YouTube has changed its layout: the ID list in `ShortsSurface.kt` needs updating, and the log will show what is on screen. |
| The block screen appears when it shouldn't | Check the log — the app never blocks a blank read, so something was read. Then allow-list that channel. |

Nothing is ever blocked based on a guess: if the title cannot be read, the Short is let
through and the log says `unknown`.

## Privacy: what this app can and cannot see

The accessibility service is a powerful permission, so here is exactly what this one does
with it.

* **No internet permission is declared in the manifest.** Not "we don't upload your data" —
  the app is incapable of making a network request. There is nothing to audit on a server
  because there is no server.
* It reads the accessibility tree of apps in the foreground, uses it to find YouTube's
  Shorts player, and extracts text from visible nodes. That is all it does with it.
* It reads **only while YouTube is in the foreground**, and it does nothing at all when the
  setting is off or the service is disabled.
* It never stores video, audio, screenshots, keystrokes, your watch history or your
  account. The detection log is a 250-entry ring buffer in RAM, wiped when the app dies.
* Everything it decides stays on the phone: the model is a 350 KB file in the APK.

## Honest limitations

* **It judges titles, not videos.** A clickbait title on a study channel can be blocked;
  a genuinely useful Short with a vague title can be let through. The block screen always
  shows what it read, so you can see which of the two just happened.
* **Detection depends on YouTube's internal view IDs** (`reel_watch_fragment_root` and
  friends). They are stable across many versions today, but YouTube can rename them, and
  then detection silently stops working until the app is updated. The detection log exists
  precisely because of this.
* **YouTube renders asynchronously.** The player often appears before its title does, which
  is why the service re-reads the window up to three times before deciding. On a slow phone
  a Short may be visible for a fraction of a second before the block screen appears.
* **Study only mode is fuzzy.** The line between "academic" and "generally informative" is
  genuinely blurry (is career advice study? is finance?). Its decision boundary is the
  weakest part of the model, which is why *Informative* is the default.
* **Numbers come from a synthetic corpus.** No legal way exists to bulk-download real
  Shorts metadata without an API key, so the model trains on a generated corpus plus 157
  hand-written borderline cases and is scored on a separate 68-case calibration set. The
  quoted accuracy is on hand-written prose and is the number to trust more than the
  synthetic one. See `model/REPORT.md` for the full breakdown, including every mistake.
* **Android only.** iOS offers no way for one app to read another app's screen. This is not
  a porting problem, it is a platform restriction.

## The model

A three-class linear softmax over sparse lexical features:

| class | meaning |
|---|---|
| `study` | academic: physics, chemistry, maths, biology, CS, languages, medicine, exam and career material |
| `info` | general knowledge and practical skills: explainers, science news, finance literacy, cooking, fitness, office skills |
| `ent` | entertainment: music, comedy, gaming, vlogs, edits, memes, status videos, gossip, astrology |

Features are word unigrams and stems, adjacent word bigrams, in-word character n-grams,
prefix/suffix markers, a shared style lexicon (`revision`, `explained`, `solved` vs
`vlog`, `prank`, `gone wrong`), channel-type markers that work on channels the model has
never seen, and multi-word phrases. Training is averaged SGD with AdaGrad; the averaged
weight vector is quantised to int16 and written as a flat lookup table, so the phone does
one hash-map pass and a softmax per decision. No TensorFlow, no ONNX, no native library,
no JNI, no wake-locks.

Decisions use a **margin** (log-odds in favour of keeping) rather than a probability
threshold, because the model is extremely confident on its training distribution and a
probability threshold would be meaningless there:

```
margin = log P(study or info) - log P(ent)      keep if margin >= threshold
```

Measured on the held-out sets (`python3 model/train.py` regenerates this table):

| set | useful content blocked | junk let through |
|---|---|---|
| 185 hand-written borderline titles | **1.7%** | **1.5%** |
| unseen topics, synthetic | 0.4% | 2.6% |

98.4% of the hand-written cases are decided correctly. The three mistakes, the trade-off
curve the strictness slider moves, and the per-class breakdown are all in
[`model/REPORT.md`](model/REPORT.md) — including the two cases whose labels were corrected
after the first evaluation, in the open.

### Retraining it (optional, ~30 seconds)

```bash
python3 model/train.py          # standard library only, no pip install, no GPU
python3 model/keywords.py android/app/src/main/assets/keywords.txt
cp model/model.bin model/model.meta android/app/src/main/assets/
python3 model/classify.py "Krebs cycle explained in 60 seconds | NEETprep"
```

Want to teach it something? Add lines to `model/dataset.py` (topics and templates) or
`model/keywords.py`, add cases to `model/hard_cases.py` for the situations you care about,
retrain, and read the report. Teaching by example is deliberately the easiest thing to do
in this repository.

## Repository layout

```
model/                 everything about the classifier (Python, standard library only)
  dataset.py           synthetic corpus generator, including a Devanagari/Hinglish slice
  keywords.py          the shared style lexicon -> android/app/src/main/assets/keywords.txt
  features.py          THE feature spec (mirrored by Features.kt, checked by ParityTest)
  train.py             trains, quantises, evaluates, writes model.bin/.meta/.json/REPORT.md
  hard_cases.py        185 hand-written borderline titles, never trained on
  calibration.py       68 more, used only to choose the shipped thresholds
  classify.py          terminal classifier, for checking a title without a phone
  tune.py              sweeps the training knobs and reports what they change
android/               the Android app (Kotlin, one service, no runtime dependencies)
  app/src/main/java/com/shortsense/
    nlp/               Features, Lexicon, TinyModel, ModelMeta, Classifier
    service/           ShortsSurface (detection), BlockOverlay, ShortsWatcherService
    MainActivity.kt    settings          DebugActivity.kt  detection log
  app/src/test/        parity, feature, and classifier behaviour tests
.github/workflows/     retrain, test, build the APK, attach it to releases
```

## Building it yourself

```bash
cd android
./gradlew testDebugUnitTest    # includes the Python/Kotlin parity test
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

Or open the `android/` folder in Android Studio (AGP 8.5.2, Kotlin 1.9.24, JDK 17).
The `preBuild` step copies `model/model.bin` and `model/model.meta` into the app's assets,
so the APK can never ship a different model than the one the Python produced.

CI retrains the model from source on every push and then runs the tests — if the Kotlin
feature extractor and the Python trainer ever disagree, the build fails. That is what
`model/parity.txt` and `ParityTest` are for.

## Credits

The Shorts-detection view IDs and the hard-won knowledge about their flakiness come from
the open-source short-form-blocker community, notably
[Shorts-Blocker](https://github.com/Atick-Faisal/Shorts-Blocker),
[PureShield](https://github.com/abdullah09c/PureShield-Stable),
[MindGuard](https://github.com/Ashish-CodeJourney/MindGuard),
[Nudge](https://github.com/astraedus/nudge),
[zenwell](https://github.com/Sarangem/zenwell) and the
[StackOverflow thread on the asynchronous-render problem](https://stackoverflow.com/questions/79400328).
The Gradle wrapper files are Gradle's own (Apache-2.0).

## Ideas that would make it better

* Read the video *description* from the engagement panel as a second signal for Shorts
  whose titles are useless.
* Learn from the user's own answers: "keep watching anyway" is a labelled example, and the
  model could be nudged with it locally.
* Per-channel statistics, so a channel that is right 19 times out of 20 needs no manual
  allow-listing.
* A one-tap "why was this blocked" share sheet, so layout breakage after a YouTube update
  can be reported with the accessibility dump attached.
