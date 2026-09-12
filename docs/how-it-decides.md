# How ShortsSense decides to block a Short

You asked how it actually decides. This is the whole mechanism, with the real numbers
the app ships with today.

## 1. What it can see

While a Short is playing, the app reads the accessibility tree of the YouTube window and
takes **two things**:

* the **title** — the text YouTube paints on the Short (the description-like line),
* the **channel** — the channel row, with things like a trailing "Subscribe" stripped off.

That is the whole input. No video, no audio, no history, no account. If the title cannot
be read, the app keeps the Short and logs `unknown`: it never blocks a guess.

## 2. What it does with them

The title and channel are turned into ~20 000 possible features, of which one Short uses
maybe 60:

| feature | example from "Newton's laws in 60 seconds #physics #jee" |
|---|---|
| `w:` a word | `w:physics`, `w:newton` |
| `st:` a word stem | `st:physic`, `st:newton` |
| `b:` a pair of neighbouring words | `b:in_60`, `b:newton_laws` |
| `ph:i:` / `ph:e:` a phrase from the shipped keyword list | `ph:i:how_to`, `ph:e:auto_renews` |
| `ch:i:` / `ch:e:` the channel name | `ch:i:physics_wallah` |
| `c3:` / `c4:` / `p2:` / `s3:` letter fragments | `c3:con`, `p2:in` |

Every feature has a weight for each of the three classes — **study**, **info** (useful
but not academic) and **ent** (entertainment) — learned offline from a synthetic corpus and
quantised to whole numbers. The phone just adds up the weights of the features it found.

## 3. The decision

The three sums become one number, the **margin**:

```
margin = log-odds that this is study-or-info rather than entertainment

keep   if margin >= threshold      (Balanced ships with threshold = -2.12)
block  otherwise
```

So a Short is blocked when the entertainment weights of its features outweigh the
useful weights by more than the threshold allows. Nothing is ever decided by a single
word: it is a sum over everything the app can see, and a title usually pulls in both
directions.

Worked examples, computed from the model that ships (Balanced, threshold −2.12):

| Short | margin | verdict | what moved it |
|---|---|---|---|
| `can you solve this mole concept question?` — Chemistry Adda | **+49.1** | keep | `w:concept`, `st:concept`, `ph:i:how_to`, study words in the channel name |
| `Newton's laws in 60 seconds #physics #jee` — Physics Wallah | **+26.3** | keep | `ph:i:#physics`, `w:physics`, `ch:i:physics_wallah` |
| `Boom Shaka · KR$NA & Dhanda Nyoliwala` — Trap Nation | **−9.5** | block | `c3:boo`, `w:trap`, `w:nation` — a slowed song upload, and the channel is a music channel |
| `Amazon Music Unlimited - 3 months free. Auto-renews at ₹119/month` — Deals India Daily | **−53.7** | block | `ph:e:auto_renews`, `ph:e:auto_renews`, `w:unlimited`, `w:renews` |

There is no hidden rule list — just this sum, this threshold, and the exceptions below.
Two things changed the ranking outright, both found in a real device export: `the`/`of`/`to`
used to carry weights of their own (a roast Short was kept because "the" scored +4), and
two-letter fragments learned from other words ("action" inheriting junk from "reaction
video"). Function words no longer have their own weights and the fragments are gone, which
took the hand-written set from 98.5% to 99.5% and the useful-block rate to zero.

## 4. The exceptions, in priority order

1. **Your channel lists win over everything.** Allow-listed → always kept. Block-listed →
   always blocked. Nothing else is even consulted.
2. **Unreadable screen → keep.** `unknown` verdicts are logged and counted, never blocked.
3. **A blank title, or fewer than two words → keep.** There is not enough to judge, so it
   does not judge. `#shorts` alone cannot be classified, and pretending otherwise is how
   blockers earn their bad reputation.
4. **Same Short, 4-second cooldown.** One block per Short per 4 seconds, so a repainted
   screen cannot stack block screens.
5. **You kept it → 60 seconds of peace.** Tapping *Keep watching anyway* suppresses that
   exact Short for a minute. Three taps on the same channel allow-list it.
6. **Rate limit.** More than 25 blocks a minute and the service pauses for a minute and
   says so in the log, rather than covering a whole scroll session.

## 5. What the strictness setting changes

Only the number in step 3. Nothing else. Measured on the 206 hand-written borderline
titles and real device cases that ship with the model:

| rung | threshold | useful Shorts blocked | junk let through |
|---|---|---|---|
| Fewest interruptions | −10.1 | 0.0 % | 11.4 % |
| Gentle | −6.1 | 0.0 % | 7.6 % |
| **Balanced** (default) | **−2.1** | **0.0 %** | **1.3 %** |
| Strict | +1.9 | 4.7 % | 0.0 % |
| Maximum filtering | +5.9 | 6.3 % | 0.0 % |

If you are on **Strict**, expect about 5% of worth-keeping Shorts to be covered — that is the
setting doing what it says, not a bug. The app now shows this table's numbers next to each
rung, and a named button per rung instead of a slider.

*Study only* mode is a different number for a different question (is this academic
material?), and it separates much worse — study material is a subset of informative
material, so no threshold on it keeps every PCM problem while rejecting every motivational
talk. That is why the README recommends informative mode.

## 6. Where it is honestly weak

* **Titles are short.** "Intro" or "Part 2" carry no signal; the app leans on the channel.
* **Comments and sheets are read by container, not by text.** A comment reads exactly like a
  title ("they dont even specialize in running so you gotta compare him to a track runner"),
  so the app refuses to look at anything under `comment_thread`, `bottom_sheet` or an ad unit.
  It was a real bug that it did.
* **Hinglish and transliterated titles** use words the corpus has less of.
* **Letter fragments are blunt.** `c3:` and `p2:` features let the model generalise past
  words it has never seen, at the cost of occasionally reacting to a fragment. I tested
  removing them entirely (two variants, retrained, re-measured): the model did not get
  better — one variant turned two junk leaks into one extra blocked useful Short — so they
  stayed.
* **Channels change.** A channel that posts study Shorts today can post reels tomorrow;
  channel features are a hint, not a rule.
* The threshold is fitted on the hand-written set, so the 98.5 % above is lightly
  optimistic rather than a held-out score. `model/REPORT.md` says so too.

## 7. When it gets it wrong

The block screen already tells you the top four features that moved the decision and
whether they leaned useful or junk. If the reason looks wrong, the quickest fixes are:

* **Keep watching anyway** — that Short is suppressed for a minute.
* **Always allow this channel** / **Never** — one tap and the whole channel stops being
  judged, in either direction.
* **Export log** — every decision with its margin and threshold, so a genuine mistake can
  be quoted exactly (and retrained against). That file is how the model gets better: it is
  the only record of what the app got wrong on a real feed.
