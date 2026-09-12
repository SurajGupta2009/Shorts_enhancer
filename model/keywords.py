"""
Style lexicon shared between the Python trainer and the Android app.

The model generalises from *style markers*, not from topic words it has never seen:
"revision", "solved", "concept" pull a title toward informative; "vlog", "prank",
"gone wrong", "status" pull it toward entertainment. The lists below are dumped to
`android/app/src/main/assets/keywords.txt` and read by the Kotlin feature extractor,
so the two implementations can never drift apart.

Matching is done on the *stemmed* token (see features.stem) so "solved"/"solving"/
"solve" all hit the same entry.
"""

INFO_KEYWORDS = [
    # teaching / study framing
    "explained", "explain", "explanation", "concept", "revision", "revise", "revised",
    "lecture", "lesson", "tutorial", "teach", "teacher", "teacher's", "class", "classes",
    "chapter", "topic", "syllabus", "note", "notes", "summary", "summarize", "recap",
    "definition", "define", "example", "exercise", "question", "problem", "solution",
    "solve", "derivation", "derive", "proof", "prove", "formula", "equation", "numerical",
    "diagram", "step", "steps", "method", "approach", "technique", "strategy", "tip",
    "tips", "trick", "tricks", "shortcut", "hack", "hacks", "mistake", "mistakes",
    "understand", "learn", "learning", "study", "studies", "prepare", "preparation",
    "practice", "mock", "pyq", "paper", "exam", "exams", "test", "marks", "score",
    "topper", "rank", "cutoff", "merit", "viva", "semester", "grade", "board",
    "neet", "jee", "upsc", "gate", "ssc", "cat", "cet", "cbse", "icse", "ncert",
    "iit", "aiims", "cuet", "ctet", "rrb", "bank", "police",
    # subjects
    "physics", "chemistry", "biology", "maths", "math", "mathematics", "science",
    "english", "grammar", "vocabulary", "pronunciation", "hindi", "history", "geography",
    "economics", "economy", "polity", "civics", "accounting", "statistics", "algebra",
    "geometry", "calculus", "trigonometry", "probability", "mechanics", "thermodynamics",
    "optics", "organic", "inorganic", "physical", "anatomy", "physiology", "pharmacology",
    "pathology", "microbiology", "nursing", "medicine", "mbbs", "neuron", "cell", "dna",
    "genetics", "ecology", "botany", "zoology", "biochemistry", "biotechnology",
    "programming", "code", "coding", "python", "java", "javascript", "kotlin", "c++",
    "sql", "database", "dbms", "algorithm", "data", "structure", "complexity", "recursion",
    "pointer", "array", "string", "graph", "tree", "interview", "placement",
    "machine", "deep", "neural", "model", "dataset", "pipeline", "deploy", "docker",
    "software", "engineering", "system", "design", "architecture", "network", "security",
    # explanation verbs / framing
    "how", "why", "what", "when", "which", "difference", "versus", "vs", "compare",
    "reason", "cause", "effect", "proof", "analysis", "analyze", "breakdown", "decode",
    "fact", "facts", "myth", "truth", "science", "research", "study", "find",
    "beginner", "basic", "basics", "advanced", "fundamental", "core", "intuition",
    "behind", "inside", "working", "works", "work", "test", "experiment", "demo",
    "career", "job", "salary", "skill", "growth", "finance", "financial", "money",
    "investment", "invest", "investing", "sip", "mutual", "fund", "stock", "tax",
    "insurance", "loan", "credit", "budget", "inflation", "gdp", "bank", "rbi",
    "health", "nutrition", "protein", "calorie", "medical", "doctor", "treatment",
    "recipe", "ingredient", "cook", "cooking", "technique", "knead", "knife",
    "guitar", "chord", "piano", "scale", "photography", "aperture", "shutter",
    "excel", "formula", "sheet", "pivot", "resume", "linkedin", "productivity",
    "education", "educational", "vacancy", "recruitment", "notification", "admit",
    "eligibility", "scholarship", "guide", "review", "specs", "benchmark", "comparison",
    "compare", "biomechanics", "cue", "form", "drill", "mobility", "warmup", "rep",
    "macro", "protein", "diet", "nutrition", "budget", "price", "cost", "laptop",
    "mobile", "phone", "gadget", "gear", "tool", "app", "website", "resource", "book",
    "books", "material", "pdf", "course", "coaching", "tuition", "doubt", "query",
    "answer", "timetable", "schedule", "habit", "focus", "concentration", "memory",
    "recall", "naukri", "sarkari", "result", "answerkey", "cutoff", "syllabus",
    "government", "govt", "job", "jobs", "post", "posts", "vacancies", "training",
    "practice", "exercise", "workout", "routine", "plan", "planner", "steps", "guide",
    "doubt", "explained", "understand", "improve", "learn", "master", "skill", "career",
]

ENT_KEYWORDS = [
    # music / dance
    "song", "songs", "music", "lyrical", "remix", "mashup", "jukebox", "audio",
    "cover", "singing", "sing", "singer", "beats", "lofi", "slowed", "reverb",
    "dance", "dancing", "choreography", "hook", "step", "performance", "routine",
    # comedy / drama
    "comedy", "funny", "joke", "jokes", "meme", "memes", "skit", "roast", "prank",
    "cringe", "laugh", "laughing", "haha", "lol", "fails", "fail", "blooper",
    "drama", "episode", "serial", "twist", "scene", "scenes", "climax", "trailer",
    "teaser", "movie", "movies", "film", "cinema", "actress", "actor", "celebrity",
    "gossip", "spotted", "pap", "reaction", "react", "reacting", "exposed",
    # vlog / lifestyle
    "vlog", "vlogs", "vlogger", "routine", "haul", "unboxing", "unbox", "tour",
    "shopping", "lookbook", "outfit", "makeup", "beauty", "skincare", "hair",
    "fashion", "nail", "glow", "challenge", "challenges", "day", "days", "hours",
    # gaming / sports clips
    "gameplay", "gaming", "clutch", "montage", "noob", "pro", "game", "level",
    "skin", "lobby", "victory", "defeat", "highlights", "highlight", "goal",
    "penalty", "wicket", "six", "catch", "match", "prediction", "team",
    # virality bait
    "viral", "trending", "reels", "status", "attitude", "sigma", "beast", "mood",
    "edits", "edit", "transition", "compilation", "wait", "end", "watch",
    "subscribe", "like", "share", "comment", "channel", "link", "bio", "giveaway",
    "motivation", "motivational", "mindset", "millionaire", "rich", "success",
    # satisfying / asmr
    "asmr", "satisfying", "slime", "crushing", "hydraulic", "relaxing", "sleep",
    "mukbang", "eating", "tasting", "foodie", "street", "food", "biryani", "cake",
    "chocolate", "dessert", "spicy", "noodles", "pizza", "burger",
    # sad / status / emotional bait
    "sad", "love", "broken", "heart", "emotional", "story", "stories", "friend",
    "friendship", "birthday", "wedding", "couple", "bhai", "yaar",
    # astrology / gambling
    "rashifal", "horoscope", "zodiac", "tarot", "kundli", "vastu", "numerology",
    "satta", "betting", "jackpot", "lottery", "crypto", "signal", "profit",
    "guaranteed", "sureshot", "trick", "double", "money",
    "keyboard", "typing", "sleepy", "night", "3am", "meme", "memes", "reel", "reels",
    "short", "shorts", "yt", "youtube", "instagram", "facebook", "whatsapp", "trend",
    "sound", "audio", "lyrics", "video", "clips", "clip", "watch", "end", "full",
]

# Words that show up on the Shorts surface but are not part of the title/channel.
UI_NOISE = [
    "shorts", "subscribe", "subscribed", "like", "likes", "dislike", "share",
    "remix", "comments", "comment", "save", "saved", "thanks", "description",
    "views", "view", "ago", "home", "explore", "library", "music", "sound",
    "original", "trending", "play", "pause", "next", "previous", "duration",
    "live", "sponsored", "advertisement", "mute", "unmute", "settings", "more",
    "reply", "replies", "post", "posted", "follow", "following", "for", "you",
    "suggested", "recommended", "watch", "full", "video", "videos", "use",
    "open", "app", "notification", "notifications", "search", "back", "close",
]

# Channel-name tokens that say "this account teaches something" or "this account is
# entertainment", matched as a separate feature kind so an unseen channel name still
# carries a usable signal ("Jeff Nippard" is not in any list, but "Physics Girl" is).
EDU_CHANNEL_WORDS = [
    "academy", "academics", "classes", "class", "classroom", "coaching", "tuition",
    "institute", "institution", "school", "college", "university", "campus", "study",
    "studies", "studygram", "student", "students", "learn", "learning", "learner",
    "education", "educational", "edu", "gyan", "vigyan", "vigyaan", "vidya", "shiksha",
    "padhai", "padhaku", "topper", "notes", "notebook", "exam", "exams", "neet", "jee",
    "upsc", "ias", "gate", "ssc", "cbse", "icse", "nptel", "mit", "iit", "professor",
    "physics", "chemistry", "biology", "maths", "math", "science", "sciencechannel",
    "english", "grammar", "vocabulary", "medical", "medico", "doctor", "nursing",
    "coding", "code", "programming", "developer", "tech", "technology", "engineering",
    "tutorials", "tutorial", "lessons", "lesson", "masterclass", "smp", "facts", "fact",
    "explainer", "explained", "knowledge", "info", "infopedia", "curious", "research",
    "lab", "physicswallah", "unacademy", "vedantu", "byjus", "drishti", "adda247",
    "kitchen", "recipes", "recipe", "cooking", "chef", "baking", "guitar", "piano",
    "musiclessons", "fitness", "workout", "yoga", "gym", "nutrition", "health",
    "finance", "varsity", "investing", "invest", "money", "tax", "legal", "law",
]

ENT_CHANNEL_WORDS = [
    "vlogs", "vlog", "vlogger", "tv", "tvshow", "comedy", "comic", "funny", "funnies",
    "masti", "entertainment", "music", "records", "beats", "audio", "songs", "song",
    "films", "film", "movies", "movie", "cinema", "pictures", "studios", "clips",
    "clip", "highlights", "gaming", "games", "gamer", "esports", "playz", "army",
    "status", "statuses", "edits", "editor", "creators", "reaction", "reactions",
    "pranks", "prank", "memes", "meme", "trending", "viral", "reels", "shortz",
    "star", "stars", "celebrity", "gossip", "updates", "buzz", "hungama", "filmy",
    "motivation", "sigma", "beast", "asmr", "satisfying", "relaxing", "mukbang",
    "foodie", "eat", "eating", "tasty", "yummy", "kitchenqueen", "dance", "dancer",
    "danceplus", "naach", "fitnessfreak", "beastmode", "attitude", "shayari", "love",
    "sad", "emotional", "stories", "story", "kahani", "astrology", "rashifal", "tarot",
    "betting", "satta", "lottery", "crypto", "trading", "tips", "prediction",
]

# Multi-word phrases worth spotting (matched on the normalised title string)
INFO_PHRASES = [
    "how to", "why do", "why does", "why is", "what is", "what are", "difference between",
    "step by step", "in 60 seconds", "in 2 minutes", "explained in", "one shot",
    "important question", "previous year", "important for", "must know", "for beginners",
    "tips and tricks", "learn how", "how i scored",
]

ENT_PHRASES = [
    "wait for it", "gone wrong", "part 2", "part 3", "full video", "must watch",
    "watch till end", "tag your", "best of", "too good", "so funny", "for the first time",
    "my reaction", "exposed", "viral video", "trending now", "no way", "oh my god",
    "who else", "what happens next", "you won't believe", "at 3am", "hits different",
]


def dump(path):
    """Write keywords.txt for the Android asset folder."""
    lines = ["# ShortsSense keyword lexicon v1 (generated by model/keywords.py)"]
    for tag, words in (("info", INFO_KEYWORDS), ("ent", ENT_KEYWORDS),
                       ("noise", UI_NOISE), ("chaninfo", EDU_CHANNEL_WORDS),
                       ("chanent", ENT_CHANNEL_WORDS)):
        lines.append("#section " + tag)
        for w in sorted(set(words)):
            lines.append(tag + "\t" + w)
    lines.append("#phrases")
    for tag, phrases in (("info", INFO_PHRASES), ("ent", ENT_PHRASES)):
        for p in sorted(set(phrases)):
            for word in p.split():
                pass
            lines.append("phrase\t" + tag + "\t" + p)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return len(lines) - 1


if __name__ == "__main__":
    import sys
    target = sys.argv[1] if len(sys.argv) > 1 else "../android/app/src/main/assets/keywords.txt"
    print("wrote", target, dump(target), "entries")
