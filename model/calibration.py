"""
Calibration set: natural-prose Shorts titles used ONLY to choose the shipped thresholds.

`hard_cases.py` is the reporting set - it is what gets quoted in REPORT.md and what the
Kotlin tests assert against. This file exists so the default thresholds are not fitted on
the same examples that are used to claim accuracy.

informative: 1 = keep (study / knowledge / learnable skill), 0 = block
study       : 1 = academic (used by the "Study only" mode)
"""

CALIBRATION_CASES = [
    # academic (study = 1)
    dict(title="gravitation: why satellites don't fall down", channel="Vigyan Recharge", informative=1, study=1),
    dict(title="redox reactions in 60 seconds | class 11", channel="Chemistry Adda", informative=1, study=1),
    dict(title="how to attempt a 3 hour paper without panic", channel="Exam Veda", informative=1, study=1),
    dict(title="limits basics you should know before calculus", channel="Maths Wallah", informative=1, study=1),
    dict(title="what is a pointer in C — with a diagram", channel="Neso Academy", informative=1, study=1),
    dict(title="DNA replication: the whole process simplified", channel="NEETprep", informative=1, study=1),
    dict(title="phrasal verbs for daily conversation", channel="BBC Learning English", informative=1, study=1),
    dict(title="how many hours a day should you actually study", channel="Padhaku Vimaan", informative=1, study=1),
    dict(title="thermodynamics: first law explained on the board", channel="Physics Wallah", informative=1, study=1),
    dict(title="removing a doubt in rotation — full solution", channel="Unacademy JEE", informative=1, study=1),
    dict(title="banking awareness: repo rate vs reverse repo", channel="Adda247", informative=1, study=1),
    dict(title="why your mock test score is dropping", channel="Study IQ Education", informative=1, study=1),
    dict(title="python loop patterns every beginner must know", channel="CodeWithHarry", informative=1, study=1),
    dict(title="mitosis vs meiosis — the difference in one minute", channel="Magnet Brains", informative=1, study=1),
    dict(title="how to read an ECG strip step by step", channel="Medico Academy", informative=1, study=1),
    dict(title="IELTS writing task 2 structure that scores 8", channel="English with Lucy", informative=1, study=1),
    dict(title="maths olympiad style question solved", channel="Numberphile", informative=1, study=1),
    dict(title="class 10 science: our environment revision", channel="Learnohub Class 11", informative=1, study=1),
    dict(title="interview question: reverse a linked list", channel="Take U Forward", informative=1, study=1),
    dict(title="how to make a revision timetable that you'll follow", channel="Topper's Notebook", informative=1, study=1),
    dict(title="ICSE board exam pattern explained", channel="Study Corner", informative=1, study=1),
    dict(title="common grammar mistakes in English essays", channel="Spoken English Guru", informative=1, study=1),
    dict(title="what is the difference between RAM and ROM", channel="Gate Smashers", informative=1, study=1),
    dict(title="solving a projectile motion numerical", channel="Physics Wallah", informative=1, study=1),
    dict(title="सामान्य ज्ञान: भारतीय संविधान के अनुच्छेद", channel="स्टडी आईक्यू", informative=1, study=1),
    dict(title="गणित: समाकलन का आसान तरीका", channel="गणित वाला", informative=1, study=1),

    # informative but not academic (study = 0)
    dict(title="why does the moon look bigger near the horizon", channel="Veritasium", informative=1, study=0),
    dict(title="how mutual funds charge you fees", channel="Zerodha Varsity", informative=1, study=0),
    dict(title="the reason planes still use jet engines", channel="Real Engineering", informative=1, study=0),
    dict(title="what happens to your body when you stop sugar", channel="Institute of Human Anatomy", informative=1, study=0),
    dict(title="a beginner's guide to buying a second hand car", channel="Labour Law Advisor", informative=1, study=0),
    dict(title="how to hold a knife without cutting yourself", channel="Your Food Lab", informative=1, study=0),
    dict(title="why compound interest beats a salary hike", channel="Finance with Sharan", informative=1, study=0),
    dict(title="learning the fretboard: guitar for beginners", channel="Justin Guitar", informative=1, study=0),
    dict(title="excel: clean messy data in one minute", channel="Leila Gharani", informative=1, study=0),
    dict(title="what caused inflation in 2026", channel="Economics Explained", informative=1, study=0),
    dict(title="how to fix your posture while working", channel="Squat University", informative=1, study=0),
    dict(title="what the Indus Valley seals actually say", channel="OverSimplified", informative=1, study=0),
    dict(title="CPR: the first two minutes matter", channel="First Aid Basics", informative=1, study=0),
    dict(title="why rockets are still so expensive", channel="Wendover Productions", informative=1, study=0),
    dict(title="साइंस: ब्लैक होल क्या होता है", channel="विज्ञान दर्शन", informative=1, study=0),

    # entertainment (junk)
    dict(title="my sister's reaction to my new haircut 😂", channel="Sourav Joshi Vlogs", informative=0, study=0),
    dict(title="wait for the beat drop 🔥", channel="Punjabi Beats", informative=0, study=0),
    dict(title="POV: your mom calls you for dinner", channel="Meme Factory India", informative=0, study=0),
    dict(title="last over finish that broke the internet", channel="Star Sports Clips", informative=0, study=0),
    dict(title="she said yes ❤️ proposal vlog", channel="Couple Vibes TV", informative=0, study=0),
    dict(title="trying the world's spiciest noodles", channel="Mukbang India", informative=0, study=0),
    dict(title="best clutch of the season", channel="BGMI Highlights", informative=0, study=0),
    dict(title="new song teaser is here", channel="T-Series", informative=0, study=0),
    dict(title="gym transformation 6 months", channel="Gym Motivation Beast", informative=0, study=0),
    dict(title="this prank went too far", channel="Prank King", informative=0, study=0),
    dict(title="bigg boss tonight's drama", channel="Bollywood Updates", informative=0, study=0),
    dict(title="5 minute craft for your room", channel="5 Minute Crafts", informative=0, study=0),
    dict(title="satisfying paint mixing compilation", channel="Oddly Satisfying", informative=0, study=0),
    dict(title="aaj ka mithun rashifal", channel="Astrology Rashi Today", informative=0, study=0),
    dict(title="sure shot fantasy team today", channel="Fantasy Prediction Pro", informative=0, study=0),
    dict(title="emotional status for whatsapp", channel="Sad Status Video", informative=0, study=0),

    # borderline: exam memes / motivation bait / movie explainers (should still block)
    dict(title="when the syllabus is huge and the exam is tomorrow 😭", channel="Meme Factory India", informative=0, study=0),
    dict(title="study hard motivation status 🔥", channel="Success Quotes Daily", informative=0, study=0),
    dict(title="movie climax explained in Hindi", channel="Movie Explained Hindi", informative=0, study=0),
    dict(title="physics teacher roasting the class", channel="Triggered Insaan", informative=0, study=0),
    dict(title="sigma rule for students", channel="Sigma Motivation Hindi", informative=0, study=0),
    dict(title="reacting to my old school photos", channel="CarryMinati", informative=0, study=0),

    # borderline: should be kept
    dict(title="why do we forget what we studied", channel="Big Think", informative=1, study=1),
    dict(title="exam day mistakes that cost marks", channel="Vision IAS", informative=1, study=1),
    dict(title="how NASA lands a rover — engineering breakdown", channel="Real Engineering", informative=1, study=0),
    dict(title="trading vs investing: what nobody tells beginners", channel="CA Rachana Ranade", informative=1, study=0),
    dict(title="learn 5 new English words today", channel="English with Lucy", informative=1, study=1),
]
