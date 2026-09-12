"""
Synthetic-but-realistic dataset generator for ShortsSense.

There is no way to legally bulk-download real YouTube Shorts titles without an API key,
so we build a broad, varied corpus from templates. The important part is *coverage*:
the corpus deliberately includes the confusion pairs that a shortcut classifier gets
wrong (study motivation vs sigma edits, "movie explained" vs film scenes, exam memes vs
exam tips, lab experiments vs satisfying slime, DSA tips vs gaming clips ...).

Domains
-------
study  : exam prep, school/college subjects, DSA, revision, formulas, viva, notes
info   : general knowledge, science news, explainers, finance literacy, health literacy
skill  : learnable practical skills that are not academic (cooking, guitar, gym form, Excel)
ent    : entertainment, music, comedy, gaming, vlogs, gossip, edits, status videos

Labels
------
informative_head : 1 for study/info/skill, 0 for ent
study_head       : 1 for study only, 0 for everything else
"""

import random

# --------------------------------------------------------------------------------------
# Channel names
# --------------------------------------------------------------------------------------

INFORMATIVE_CHANNELS = {
    "study": [
        "Physics Wallah", "PW Foundation", "Vedantu JEE", "Vedantu NEET", "Unacademy JEE",
        "Unacademy UPSC", "Aakash Institute", "Allen Career Institute", "Byju's Classes",
        "Khan Academy India", "Khan Academy", "Toppr Study", "Doubtnut", "Study IQ Education",
        "Adda247", "SSC Wallah", "Exam Fear", "Learnohub Class 11", "Next Toppers",
        "Magnet Brains", "Study Buddy Club", "Chemistry Adda", "Organic Chemistry Tutor",
        "Maths Wallah", "GATE Smashers", "Jenny's Lectures CS IT", "Apna College",
        "CodeWithHarry", "Take U Forward", "Striver A2Z", "Love Babbar", "Neso Academy",
        "Knowledge Gate", "Ravindrababu Ravula", "Abdul Bari Algorithms",
        "Aman Dhattarwal", "Anuj Bhaiya", "Coding Ninjas", "Pepcoding", "Corey Schafer",
        "Programming with Mosh", "freeCodeCamp", "NeetCode", "CS Dojo", "ByteByteGo",
        "StatQuest", "Krish Naik", "CampusX Machine Learning", "DeepLearningAI",
        "NPTEL IIT Lectures", "IIT Madras Online", "MIT OpenCourseWare", "Learn English with Emma",
        "English with Lucy", "BBC Learning English", "Spoken English Guru",
        "Study Corner", "Padhaku Vimaan", "Vigyan Recharge", "Sarrthi IAS", "Drishti IAS",
        "Vision IAS", "Vajiram and Ravi", "PW Udaan", "Board Exam Hub", "Class 10 Shiksha",
        "NEETprep", "Doctor's Study Room", "Medico Academy", "Anatomy with Dr Nidhi",
        "Marathon Academy", "Revision Point", "Smart Study Hub", "Concept Clarity",
        "Exam Veda", "Score High Academy", "Learning Hub India", "Study with Meera",
        "Topper's Notebook", "Padhai Ka Funda", "Solution Point", "Numerical Ninja",
    ],
    "info": [
        "Veritasium", "Kurzgesagt In a Nutshell", "3Blue1Brown", "Numberphile",
        "Computerphile", "Sixty Symbols", "MinutePhysics", "PBS Space Time",
        "SciShow", "SciShow Space", "CrashCourse", "TED-Ed", "TED", "Real Engineering",
        "Practical Engineering", "Sabine Hossenfelder", "PBS Eons", "SmarterEveryDay",
        "Mark Rober", "Steve Mould", "Physics Girl", "The Organic Chemistry Tutor",
        "Half as Interesting", "Wendover Productions", "Economics Explained",
        "Moneycontrol Explainers", "CA Rachana Ranade", "Labour Law Advisor",
        "Finance with Sharan", "Zerodha Varsity", "CA Parag Gupta", "Doctor Mike",
        "Institute of Human Anatomy", "Healthcare Triage", "Nutrition Made Simple",
        "Dhruv Rathee Explains", "Abhi and Niyu", "ThePrint", "Firstpost Vantage",
        "Down to Earth", "PIB Fact Check", "Sanjeev Sanyal", "Bharat Explained",
        "Geography Now", "Atlas Pro", "RealLifeLore", "History Matters", "OverSimplified",
        "Ancient Americas", "Kings and Generals", "Epic History TV", "Nile Red",
        "Chemistry Shorts", "Periodic Videos", "Verge Science", "SciShow Psych",
        "Big Think", "Lex Fridman Clips", "Quanta Magazine", "The Royal Institution",
        "NASA Goddard", "ISRO Official", "SpaceX Updates", "Vox Explainers",
        "Kurzgesagt Hindi", "Vigyan Darshan", "Gyaan Vigyaan", "Science Ki Duniya",
        "Curious Mind", "Fact Science Hindi", "InfoBytes India", "Explainer Daily",
    ],
    "skill": [
        "Binging with Babish", "Chef Ranveer Brar Shorts", "Hebbars Kitchen",
        "Kabita's Kitchen", "Your Food Lab", "Gordon Ramsay Shorts", "Nino's Home",
        "Yoga with Adriene", "Sakshi Yoga", "Fitness with Anurag", "Jeff Nippard",
        "Squat University", "Calisthenic Movement", "Guitar Zero to Hero", "Justin Guitar",
        "Piano in 21 Days", "Anish Guitar Lessons", "Photography with Kunal",
        "Mango Street", "Peter McKinnon", "Excel Is Fun", "Leila Gharani",
        "Kevin Stratvert", "Microsoft 365 Tips", "Canva Design School", "Figma Tutorials",
        "DIY Creators", "Woodworking for Mere Mortals", "Home Repair Tutor",
        "Skillshare Sessions", "How To Men", "First Aid Basics", "Learn Touch Typing",
        "Personal Finance Basics", "Minimalist Living Tips", "Gardening for Beginners",
    ],
}

JUNK_CHANNELS = {
    "ent": [
        "T-Series", "Zee Music Company", "Saregama Music", "Sony Music India",
        "Speed Records", "Desi Music Factory", "CarryMinati", "BB Ki Vines",
        "Ashish Chanchlani Vines", "Triggered Insaan", "Harsh Beniwal", "Elvish Yadav",
        "Bharti TV", "Funny Comedy Squad", "Manoj Dey Comedy", "Angry Prash",
        "Not Your Type", "Sourav Joshi Vlogs", "Flying Beast", "Technical Guruji Vlogs",
        "Mumbiker Nikhil", "Gaurav Taneja Vlogs", "Dance Plus Clips", "Team Naach",
        "MJ5 Official", "Pranit Dance", "BGMI Highlights", "Total Gaming",
        "Ajjubhai Gamer", "Techno Gamerz", "Free Fire India", "Minecraft Hindi",
        "GTA 5 Clips", "Dynamo Gaming", "Cricket Highlights Zone", "Star Sports Clips",
        "Footy Clips", "Football Edits HD", "Movie Scenes HD", "Bollywood Hungama",
        "Filmigaane", "Movie Explained Hindi", "Filmy Gyan", "Tollywood Buzz",
        "Celeb Gossip Daily", "Bollywood Updates", "Entertainment Tonight India",
        "Bhojpuri Songs", "Punjabi Beats", "LoFi Chill India", "Trap Nation",
        "Viral Status Zone", "Attitude Status 4K", "Sigma Edits", "Mood Edits",
        "Love Status Hindi", "Sad Status Video", "Comedy Memes Daily",
        "Meme Factory India", "Funny Animals TV", "Try Not To Laugh",
        "Prank King", "Social Experiment Fun", "Street Food Frenzy",
        "Mukbang India", "Foodie Vlogger", "5 Minute Crafts", "Crafty Ideas",
        "Satisfying Slime ASMR", "Oddly Satisfying", "ASMR Relaxing Sounds",
        "Fashion Lookbook", "Beauty Glow Tips", "Makeup Transformation",
        "Hair Styling Ideas", "Nail Art Daily", "Gym Motivation Beast",
        "Beast Mode Motivation", "Sigma Motivation Hindi", "Success Quotes Daily",
        "Astrology Rashi Today", "Zodiac Sign Facts", "Tarot Reading Live",
        "Relationship Goals", "Couple Vibes TV", "Dating Tips Hindi",
        "Travel Diaries India", "Wanderlust Shots", "Drone Footage HD",
        "WhatsApp Status Hub", "Emotional Story Hindi", "Betting Tips Pro",
        "IPL Prediction Today", "Satta Chart Tips", "Tech Unboxing Beast",
        "Gadget Review India", "Reaction King", "Roast Central",
        "Challenge Accepted", "24 Hour Challenge", "Cringe Compilation",
        "Reels Compilation", "Trending Now India", "Viral Video Daily",
    ],
}

ALL_CHANNEL_SUFFIXES = [
    "", "", "", "", "", " Hindi", " English", " Official", " Clips", " Shorts",
    " Academy", " Classes", " TV", " HD", " 2.0", " Official Channel", " India",
]

# --------------------------------------------------------------------------------------
# Title templates per category
# --------------------------------------------------------------------------------------

STUDY_GROUPS = [
    ("physics", "science", [
        "Newton's second law", "projectile motion", "Ohm's law", "Faraday's law",
        "Snell's law", "moment of inertia", "rotational motion", "Bernoulli's theorem",
        "kinetic theory of gases", "photoelectric effect", "Bohr model", "de Broglie wavelength",
        "escape velocity", "centre of mass", "work energy theorem", "circular motion",
        "thermodynamics first law", "entropy", "simple harmonic motion", "wave optics",
        "total internal reflection", "capacitor charging", "LCR circuit resonance",
        "magnetic force on a wire", "Galvanometer to ammeter", "emf vs potential difference",
        "surface tension", "viscosity Stokes law", "Doppler effect", "nuclear binding energy",
    ], [
        "{topic} explained in 60 seconds",
        "{topic} — full concept in 1 minute",
        "{topic} ka proof | {topic} derivation",
        "JEE Advanced question on {topic}",
        "NEET physics trick for {topic}",
        "Class 12 physics: {topic} revision",
        "3 mistakes students make in {topic}",
        "{topic} numericals solved step by step",
        "Board exam question from {topic}",
        "formula of {topic} — remember it forever",
        "{topic} ka asli concept | {topic} visualised",
        "why is {topic} important for JEE",
        "{topic} one shot revision",
        "{topic} — 5 min concept clarity",
        "physics question: solve {topic} in 30 seconds",
        "teacher explains {topic} on board",
        "PYQ on {topic} with solution",
        "{topic} board exam weightage",
        "{topic} short trick for competitive exams",
        "numerical practice: {topic}",
    ]),
    ("chemistry", "science", [
        "hybridisation", "Mole concept", "chemical bonding", "Le Chatelier principle",
        "periodic table trends", "SN1 vs SN2 reaction", "Markovnikov rule",
        "aromaticity Huckel rule", "electrochemical series", "Nernst equation",
        "coordination compounds", "IUPAC nomenclature", "aldol condensation",
        "titration calculation", "buffer solution pH", "rate law and order of reaction",
        "salt analysis", "Mole fraction", "limiting reagent", "resonance structures",
        "organic reaction mechanism", "p block elements", "transition metal colours",
        "Born Haber cycle", "colligative properties", "ionic equilibrium",
    ], [
        "{topic} explained | organic chemistry",
        "{topic} in 60 seconds — NEET chemistry",
        "{topic} trick for JEE Mains",
        "class 11 chemistry: {topic} basics",
        "solve {topic} question step by step",
        "{topic} ka full concept with examples",
        "chemistry practical: {topic}",
        "most repeated {topic} question in exams",
        "{topic} revision one shot",
        "{topic} — 3 tips to never forget",
        "why {topic} is asked every year",
        "{topic} mechanism explained on board",
        "NEET 2025 {topic} important question",
        "MCQ practice: {topic}",
        "{topic} short notes revision",
    ]),
    ("maths", "math", [
        "integration by parts", "definite integral property", "limits and continuity",
        "differentiation chain rule", "binomial theorem", "permutation and combination",
        "probability bayes theorem", "matrices determinant", "complex numbers modulus",
        "conic sections parabola", "3D geometry direction cosines", "vectors dot product",
        "quadratic equation roots", "sequence and series", "logarithm properties",
        "trigonometric identities", "inverse trigonometric functions", "differential equation",
        "maxima and minima", "Rolle's theorem", "mean value theorem", "set theory venn diagram",
        "number theory divisibility", "remainder theorem", "circle tangent theorem",
        "similar triangles", "Pythagoras proof", "mensuration", "ratio and proportion",
    ], [
        "{topic} explained with proof",
        "{topic} trick for JEE Mains",
        "class 12 maths: {topic} revision",
        "{topic} — solve in 20 seconds",
        "{topic} ka shortcut | competitive exam maths",
        "why {topic} works | {topic} proof",
        "{topic} board exam important question",
        "{topic} mistake everyone makes",
        "{topic} full chapter in 1 minute",
        "SSE/CAT quantitative: {topic}",
        "{topic} using animation",
        "hard {topic} question solved",
        "{topic} formula derivation",
        "{topic} tips from a topper",
        "teacher explains {topic} in class",
        "{topic} question bank practice",
    ]),
    ("biology", "bio", [
        "photosynthesis light reaction", "Krebs cycle", "mitosis vs meiosis",
        "DNA replication", "transcription and translation", "human heart circulation",
        "nephron function", "neuron action potential", "digestion enzymes",
        "respiratory system anatomy", "endocrine glands and hormones", "blood groups",
        "Mendelian genetics", "genetic disorders", "ecology food chain",
        "plant tissue types", "cell organelles", "enzyme kinetics",
        "immunity and vaccines", "muscle contraction", "reproductive system",
        "excretory system", "botany morphology", "evolution evidence", "biotechnology PCR",
    ], [
        "{topic} explained with diagram",
        "NEET biology: {topic} in 1 minute",
        "{topic} — full concept revision",
        "{topic} diagram labelled for board exam",
        "{topic} important question for NEET",
        "{topic} trick to remember forever",
        "class 12 biology {topic} explained",
        "{topic} — score full marks",
        "{topic} question from previous year",
        "biology practical: {topic}",
        "{topic} one shot detailed lecture clip",
        "{topic} chapter summary in 60 seconds",
        "{topic} NCERT line by line",
        "why {topic} matters for NEET",
        "{topic} MCQ practice session",
    ]),
    ("cs", "cs", [
        "binary search", "time complexity", "dynamic programming memoisation",
        "graph BFS DFS", "linked list reversal", "stack and queue implementation",
        "hashing and collision", "heap sort", "merge sort", "quicksort partition",
        "recursion base case", "tree traversal", "AVL rotation", "Dijkstra algorithm",
        "SQL joins", "normalisation in DBMS", "OSI model", "TCP vs UDP",
        "process vs thread", "deadlock conditions", "paging and virtual memory",
        "object oriented pillars", "SOLID principles", "REST API design",
        "git rebase vs merge", "Docker basics", "system design load balancing",
        "LeetCode pattern", "interview question on arrays", "Python list comprehension",
        "Java vs Python for DSA", "pointer arithmetic in C", "Big O of nested loops",
    ], [
        "{topic} explained for beginners",
        "{topic} — DSA interview question",
        "{topic} in 60 seconds",
        "placements: {topic} asked in interview",
        "{topic} with code walkthrough",
        "don't make this {topic} mistake",
        "{topic} roadmap for 2025",
        "system design: {topic}",
        "{topic} dry run on whiteboard",
        "coding interview: {topic} solution",
        "{topic} full tutorial clip",
        "striver sheet: {topic}",
        "{topic} — O(n) approach explained",
        "college semester exam: {topic}",
        "{topic} concepts for GATE CSE",
        "learn {topic} in one minute",
        "why {topic} fails in production",
        "{topic} cheat sheet",
    ]),
    ("aiml", "cs", [
        "gradient descent", "overfitting and regularisation", "bias variance tradeoff",
        "neural network backpropagation", "transformer attention", "word embeddings",
        "confusion matrix", "precision vs recall", "decision tree entropy",
        "k-means clustering", "PCA dimensionality reduction", "random forest",
        "fine tuning an LLM", "prompt engineering basics", "RAG pipeline",
        "tokenisation", "convolution operation", "transfer learning",
        "feature engineering", "cross validation", "ML project deployment",
    ], [
        "{topic} explained visually",
        "{topic} in 60 seconds | machine learning",
        "{topic} — interview question for ML role",
        "AI concepts: {topic}",
        "{topic} with python code",
        "{topic} maths behind it",
        "data science: {topic} revision",
        "{topic} explained without maths",
        "why {topic} matters in real projects",
        "{topic} — project walkthrough clip",
    ]),
    ("exams", "exam", [
        "JEE Mains 2026 strategy", "NEET 2026 syllabus", "UPSC prelims strategy",
        "SSC CGL preparation", "board exam time table", "how to attempt JEE paper",
        "negative marking strategy", "90 day study plan", "last minute revision plan",
        "topper's timetable", "mock test analysis", "previous year paper analysis",
        "expected cut off", "college admission counselling", "which branch to choose",
        "MHT CET preparation", "CAT quant strategy", "GATE preparation plan",
        "CUET exam pattern", "how many hours to study",
    ], [
        "{topic} — full breakdown",
        "{topic} for students | exam update",
        "{topic} final plan",
        "important announcement for {topic}",
        "how to prepare for {topic}",
        "{topic} — what toppers do differently",
        "{topic} study plan day by day",
        "reality of {topic}",
        "{topic} tips from an IITian",
        "{topic} — avoid these blunders",
        "official update on {topic}",
        "{topic} analysis and strategy",
    ]),
    ("studytech", "study", [
        "active recall", "spaced repetition", "Pomodoro technique", "Feynman technique",
        "note making strategy", "how to revise a chapter", "how to stop phone addiction while studying",
        "concentration kaise badhaye", "morning study routine", "how to remember formulas",
        "how to study for long hours", "sleep and memory", "exam anxiety relief",
        "how to make a timetable", "best apps for students", "how to take notes on ipad",
        "study with me session", "how toppers revise", "how to avoid distraction",
        "revision before exam",
    ], [
        "{topic} | study technique",
        "{topic} — science backed method",
        "{topic} for students",
        "{topic} explained in 60 seconds",
        "how I used {topic} to top my class",
        "{topic} — 3 practical steps",
        "{topic} tips for board exam students",
        "{topic} — study smart not hard",
        "{topic} that actually works",
    ]),
    ("english", "lang", [
        "English tenses", "articles a an the", "subject verb agreement",
        "common grammar mistakes", "spoken English practice", "IELTS speaking tips",
        "vocabulary for daily use", "phrasal verbs", "pronunciation of tricky words",
        "how to speak fluent English", "email writing format", "essay writing structure",
        "translation Hindi to English", "daily use English sentences", "interview English",
    ], [
        "{topic} — learn English",
        "{topic} explained simply",
        "{topic} | English grammar lesson",
        "{topic} for competitive exams",
        "{topic} in 60 seconds",
        "{topic} — speak like a native",
        "{topic} practice with examples",
        "5 sentences using {topic}",
    ]),
    ("medlaw", "med", [
        "medical entrance biology", "first year MBBS subjects", "human anatomy basics",
        "clinical examination steps", "ECG interpretation", "pharmacology drug classification",
        "nursing procedure", "first aid for burns", "CPR steps",
        "IPC sections explained", "Indian constitution articles", "landmark judgments",
        "law entrance preparation", "contract law basics", "criminal law difference",
    ], [
        "{topic} explained for students",
        "{topic} — professional exam revision",
        "{topic} in 60 seconds",
        "{topic} step by step",
        "{topic} important for exams",
        "{topic} — must know concept",
        "{topic} viva questions",
    ]),
]

JUNK_GROUPS = [
    ("comedy", [
        "funny video", "comedy skit", "roast", "stand up bit", "meme review",
        "funny exam answer", "teacher vs student comedy", "family comedy scene",
        "desi comedy", "village comedy", "prank on friend", "public prank",
        "cringe moment", "funny fails", "try not to laugh challenge",
        "comedy status", "joke of the day", "whatsapp funny video",
    ], [
        "{topic} 😂😂",
        "{topic} — couldn't stop laughing",
        "{topic} | comedy video",
        "wait for it... {topic}",
        "{topic} ft. my friends",
        "{topic} part 2",
        "tag your friend who does {topic}",
        "{topic} 😭😭 #comedy #funny",
        "POV: {topic}",
        "when {topic} happens",
        "{topic} new comedy 2026",
        "{topic} — 1 million likes special",
    ]),
    ("music", [
        "new song", "lyrical video", "full video song", "remix", "lofi version",
        "slowed reverb edit", "whatsapp status song", "dj remix", "old is gold song",
        "heart touching song", "punjabi song", "bhojpuri song", "item song",
        "song teaser", "audio jukebox", "musical.ly trend", "singing cover",
    ], [
        "{topic} — official video",
        "{topic} | new hindi song 2026",
        "{topic} lyrical",
        "{topic} slowed + reverb",
        "{topic} status video",
        "{topic} DJ remix",
        "best of {topic}",
        "{topic} full song",
        "{topic} trending on reels",
    ]),
    ("dance", [
        "dance challenge", "hook step", "latest trend dance", "dance cover",
        "hip hop routine", "bhangra performance", "kathak performance",
        "couple dance", "wedding dance", "dance reel trend", "dance battle",
    ], [
        "{topic} 🔥",
        "{topic} — trending reels",
        "{topic} | dance cover",
        "{topic} tutorial style reel",
        "we tried {topic}",
        "{topic} beat drop",
        "{topic} viral version",
    ]),
    ("gaming", [
        "BGMI clutch", "Free Fire 1v4", "Minecraft survival", "GTA 5 stunt",
        "GTA roleplay moment", "valorant ace", "cod mobile gameplay",
        "pubg montage", "gaming funny moment", "blue whale challenge in game",
        "op gun skin", "new season update", "gameplay highlights",
        "noob vs pro", "clutch 1v5", "speedrun world record",
    ], [
        "{topic} 🤯",
        "{topic} — best gameplay",
        "{topic} highlights",
        "insane {topic}",
        "{topic} | gaming montage",
        "{topic} live reaction",
        "{topic} 4k gameplay",
        "who wins {topic}",
    ]),
    ("vlog", [
        "morning routine", "day in my life", "daily vlog", "travel vlog",
        "family vlog", "shopping haul", "unboxing", "room tour",
        "car tour", "bike ride", "train journey", "airport vlog",
        "street food tour", "cafe hopping", "sibling tag", "24 hours with",
    ], [
        "{topic} 🌸",
        "{topic} | daily vlog",
        "{topic} vlog 2026",
        "{topic} — come with me",
        "{topic} in my new house",
        "{topic} (emotional)",
        "a day of {topic}",
        "{topic} | life update",
    ]),
    ("food", [
        "street food", "mukbang", "recipe hack", "pizza challenge", "burger eating",
        "spicy noodles challenge", "cooking hack", "dessert making",
        "cake decoration", "thali review", "biriyani tasting", "fast food combo",
    ], [
        "{topic} 😋",
        "{topic} — eating show",
        "{topic} in 60 seconds",
        "{topic} food challenge",
        "{topic} review",
        "giant {topic}",
        "{topic} at 2 am",
        "trying {topic} for the first time",
    ]),
    ("fashion", [
        "outfit of the day", "makeup transformation", "hair styling", "nail art",
        "saree draping style", "party look", "skincare routine", "glow up tips",
        "shopping lookbook", "gym wear haul",
    ], [
        "{topic} 💅",
        "{topic} | new look",
        "{topic} transform",
        "{topic} under 500 rupees",
        "{topic} hack you need",
        "{topic} before after",
    ]),
    ("sports", [
        "cricket highlights", "last ball thriller", "six compilation",
        "bowling action analysis", "football skills", "goal of the season",
        "penalty shootout", "stadium atmosphere", "match prediction",
        "player celebration", "world cup moment", "top 10 catches",
    ], [
        "{topic} 🔥",
        "{topic} — match highlights",
        "{topic} best moments",
        "{topic} reaction",
        "{topic} last over drama",
        "{topic} | shorts",
    ]),
    ("movies", [
        "movie scene", "film climax", "fight scene", "best dialogue",
        "movie explained", "trailer reaction", "web series review", "movie facts",
        "behind the scenes", "entry scene", "comedy scene", "movie ending explained",
    ], [
        "{topic} 🎬",
        "{topic} — full scene",
        "{topic} in 60 seconds",
        "{topic} | must watch",
        "{topic} reaction video",
        "{topic} dialogue status",
        "{topic} explained in Hindi",
    ]),
    ("motivation_ent", [
        "gym motivation", "sigma rule", "attitude status", "success mindset",
        "hard work motivation", "millionaire mindset", "beast mode", "no excuses",
        "discipline motivation", "study motivation status",
    ], [
        "{topic} 🔥🔥",
        "{topic} status",
        "{topic} | attitude edit",
        "{topic} — watch till end",
        "{topic} for students",
        "{topic} 4k edit",
        "{topic} whatsapp status",
    ]),
    ("gossip", [
        "celebrity news", "actor interview clip", "bigg boss moment",
        "tv serial twist", "star kid spotted", "award show drama",
        "controversy explained", "pap video", "breakup news", "engagement news",
    ], [
        "{topic} 😱",
        "{topic} latest update",
        "{topic} — shocking",
        "{topic} full drama",
        "{topic} viral clip",
        "{topic} inside story",
    ]),
    ("status", [
        "love status", "sad status", "broken heart status", "friendship status",
        "emotional status", "new status video", "attitude status 4k",
        "good morning status", "festival status", "birthday status",
    ], [
        "{topic} 💔",
        "{topic} video",
        "{topic} — download now",
        "{topic} new 2026",
        "{topic} trending",
        "{topic} for whatsapp",
    ]),
    ("asmr", [
        "satisfying video", "slime asmr", "soap cutting", "hydraulic press",
        "oddly satisfying compilation", "kinetic sand", "crushing things",
        "asmr eating", "asmr tapping", "relaxing sounds",
    ], [
        "{topic} 😴",
        "{topic} — sleep fast",
        "{topic} compilation",
        "{topic} no talking",
        "{topic} for relaxation",
        "{topic} 1 hour",
    ]),
    ("crafts", [
        "5 minute craft", "life hack", "diy idea", "homemade gadget",
        "kitchen hack", "waste material craft", "school project hack",
        "glue gun idea", "paper craft", "cardboard idea",
    ], [
        "{topic} 🤩",
        "{topic} — must try",
        "{topic} at home",
        "{topic} easy diy",
        "{topic} you didn't know",
        "{topic} viral hack",
    ]),
    ("astrology", [
        "rashifal today", "zodiac sign facts", "tarot reading", "kundli prediction",
        "numerology number", "vastu tip", "palm reading", "horoscope weekly",
        "sign compatibility", "lucky colour today",
    ], [
        "{topic} 🔮",
        "{topic} — your sign",
        "{topic} today",
        "{topic} revealed",
        "{topic} 2026 prediction",
        "{topic} | must know",
    ]),
    ("betting", [
        "match prediction", "betting trick", "satta result", "jackpot trick",
        "trading tip intraday", "crypto pump signal", "double your money",
        "lottery trick", "fantasy team prediction", "sure shot tip",
    ], [
        "{topic} 💰",
        "{topic} guaranteed",
        "{topic} — 100% working",
        "{topic} today",
        "{topic} secret formula",
        "{topic} profit proof",
    ]),
    ("reaction", [
        "reaction video", "first time watching", "roast video", "comment reply",
        "youtuber drama", "exposed video", "beef between creators",
        "tiktok compilation", "instagram reels compilation", "trending compilation",
    ], [
        "{topic} 😂",
        "{topic} — my reaction",
        "{topic} exposed",
        "{topic} full video",
        "{topic} compilation 2026",
        "reacting to {topic}",
    ]),
    ("fitness_ent", [
        "gym transformation", "bodybuilding motivation", "abs workout challenge",
        "physique update", "deadlift pr", "protein diet hack", "fat loss journey",
        "gym funny moment", "pre workout energy", "shredded body status",
    ], [
        "{topic} 💪",
        "{topic} motivation",
        "{topic} status",
        "{topic} challenge",
        "{topic} transformation",
        "{topic} beast mode",
    ]),
]

HASHTAGS_ENT = ["#viral", "#trending", "#foryou", "#shorts", "#reels", "#explore",
                "#viralvideo", "#funny", "#comedy", "#status", "#love", "#music"]
HASHTAGS_INFO = ["#shorts", "#study", "#exam", "#jee", "#neet", "#upsc", "#physics",
                 "#maths", "#chemistry", "#biology", "#coding", "#dsa", "#english",
                 "#gk", "#science", "#explained", "#education", "#learning"]
EMOJIS_ENT = ["🔥", "😂", "😱", "💔", "❤️", "🙏", "😎", "✨", "😭", "💯", "🤯", "👀"]
EMOJIS_INFO = ["📚", "✍️", "🧠", "📝", "✅", "💡", "🧪", "🔬", "📐"]


# --------------------------------------------------------------------------------------
# Devanagari / Hinglish coverage. A big share of study shorts an Indian student sees are
# titled in Devanagari, and a model trained only on Latin text would fail open on them.
# --------------------------------------------------------------------------------------

DEVANAGARI_STUDY_TOPICS = [
    "न्यूटन का दूसरा नियम", "गति के समीकरण", "ओम का नियम", "प्रकाश का परावर्तन",
    "आवर्त सारणी", "मोल संकल्पना", "रासायनिक बंधन", "कार्बनिक रसायन", "समाकलन",
    "अवकलन", "निश्चित समाकल", "प्रायिकता", "निर्देशांक ज्यामिति", "त्रिकोणमिति",
    "प्रकाश संश्लेषण", "श्वसन तंत्र", "कोशिका विभाजन", "मानव हृदय", "डीएनए प्रतिकृति",
    "बाइनरी सर्च", "टाइम कॉम्प्लेक्सिटी", "डेटा स्ट्रक्चर", "एसक्यूएल जॉइन",
    "यूपीएससी प्रारंभिक परीक्षा", "सामान्य ज्ञान", "भारतीय संविधान", "अंग्रेजी ग्रामर",
    "पढ़ाई का टाइमटेबल", "याद करने की ट्रिक", "रिवीजन प्लान", "बोर्ड परीक्षा रणनीति",
]
DEVANAGARI_STUDY_TEMPLATES = [
    "{topic} 60 सेकंड में समझाया",
    "{topic} का पूरा कॉन्सेप्ट",
    "{topic} ट्रिक जेईई के लिए",
    "कक्षा 12 भौतिकी {topic}",
    "{topic} न्यूमेरिकल हल करें",
    "बोर्ड परीक्षा में {topic} का प्रश्न",
    "नीट के लिए {topic} महत्वपूर्ण",
    "{topic} के सूत्र याद रखें",
    "{topic} पढ़ाई की टिप",
    "{topic} रिवीजन वन शॉट",
    "{topic} समझें आसान भाषा में",
    "{topic} परीक्षा में हर साल आता है",
]
DEVANAGARI_STUDY_CHANNELS = [
    "फिजिक्स वाला", "खान अकादमी हिंदी", "स्टडी आईक्यू", "अपना कॉलेज",
    "कोड विद हैरी", "विद्या मंदिर", "गणित वाला", "सामान्य ज्ञान हिंदी",
    "नीट प्रेप हिंदी", "पढ़ाई का अड्डा",
]
DEVANAGARI_ENT_TOPICS = [
    "नया गाना", "कॉमेडी वीडियो", "हंसी भरा स्किट", "शादी का वीडियो", "डांस चैलेंज",
    "बीजीएमआई क्लच", "मूवी सीन", "रोमांटिक स्टेटस", "फनी क्लिप", "ट्रेंडिंग रील",
    "सैड स्टेटस", "भोजपुरी गाना", "मोटिवेशन स्टेटस", "राशिफल आज", "स्ट्रीट फूड टूर",
    "फैशन हैक", "फ्री फायर गेमप्ले", "मजेदार प्रैंक", "आईपीएल हाइलाइट्स",
]
DEVANAGARI_ENT_TEMPLATES = [
    "{topic} 😂",
    "{topic} नया वीडियो",
    "{topic} वायरल",
    "{topic} फुल एचडी",
    "देखिए {topic}",
    "{topic} स्टेटस",
    "{topic} पार्ट 2",
    "{topic} ट्रेंडिंग",
    "{topic} ऐसा ही होता है",
]
DEVANAGARI_ENT_CHANNELS = [
    "टी सीरीज", "कॉमेडी किंग", "म्यूजिक इंडिया", "बॉलीवुड अपडेट", "गेमिंग हिंदी",
    "ट्रेंडिंग इंडिया", "मस्ती टीवी", "स्टेटस ज़ोन",
]


def augment_devanagari(rng, count=2200):
    """Extra rows in Devanagari script, half study half entertainment."""
    rows = []
    seen = set()

    def push(title, channel, domain, group):
        key = (title, channel)
        if key in seen:
            return
        seen.add(key)
        rows.append({
            "title": title, "channel": channel, "domain": domain, "group": group,
            "informative": 0 if domain == "ent" else 1,
            "study": 1 if domain == "study" else 0,
        })

    half = count // 2
    for _ in range(half):
        topic = rng.choice(DEVANAGARI_STUDY_TOPICS)
        tpl = rng.choice(DEVANAGARI_STUDY_TEMPLATES)
        title = tpl.format(topic=topic)
        if rng.random() < 0.3:
            title += " " + rng.choice(["#shorts", "#पढ़ाई", "#exam", "📚"])
        push(title, rng.choice(DEVANAGARI_STUDY_CHANNELS), "study", "devanagari")
    for _ in range(half):
        topic = rng.choice(DEVANAGARI_ENT_TOPICS)
        tpl = rng.choice(DEVANAGARI_ENT_TEMPLATES)
        title = tpl.format(topic=topic)
        if rng.random() < 0.3:
            title += " " + rng.choice(["#viral", "#trending", "🔥", "#reels"])
        push(title, rng.choice(DEVANAGARI_ENT_CHANNELS), "ent", "devanagari")

    rng.shuffle(rows)
    return rows


def _maybe(random_obj, value, p=0.3):
    return value if random_obj.random() < p else ""


def _mutate_channel(rng, name):
    if rng.random() < 0.25:
        name = name + rng.choice(ALL_CHANNEL_SUFFIXES)
    if rng.random() < 0.06:
        name = name + " " + str(rng.randint(1, 99))
    return name.strip()


def _decorate(rng, title, domain):
    """Add the kind of noise real Shorts titles carry."""
    out = title
    if rng.random() < 0.30:
        out = out + " " + rng.choice(HASHTAGS_INFO if domain != "ent" else HASHTAGS_ENT)
    if rng.random() < 0.35:
        out = out + " " + rng.choice(EMOJIS_INFO if domain != "ent" else EMOJIS_ENT)
    if rng.random() < 0.18:
        out = out + " " + rng.choice(HASHTAGS_INFO if domain != "ent" else HASHTAGS_ENT)
    if rng.random() < 0.10 and domain != "ent":
        out = out + " #shorts"
    if rng.random() < 0.06:
        out = out.upper()
    if rng.random() < 0.08:
        out = out.replace(" ", "", rng.randint(1, 2))
    return out.strip()


def _typo(rng, text):
    """Occasional misspelling / Hinglish spelling noise."""
    swaps = [("ph", "f"), ("tion", "shn"), ("cs", "x"), ("oo", "u")]
    if rng.random() < 0.10:
        for a, b in swaps:
            if a in text:
                text = text.replace(a, b, 1)
                break
    if rng.random() < 0.05:
        text = text + rng.choice([" ?", " !!", " .."])
    return text



# --------------------------------------------------------------------------------------
# Natural-prose templates. Real Shorts titles are sentences, not "topic + keyword", so a
# large share of the corpus uses these to teach the model *style* rather than topic words.
# --------------------------------------------------------------------------------------

NATURAL_STUDY_TEMPLATES = [
    "why {topic} happens",
    "what is {topic} — explained simply",
    "{topic} — the only video you need before the exam",
    "understand {topic} in 2 minutes",
    "{topic} for beginners | part 1",
    "the correct way to learn {topic}",
    "{topic} explained by a teacher on the board",
    "questions on {topic} you must solve",
    "common mistakes students make in {topic}",
    "{topic} — quick revision before the exam",
    "the {topic} basics you forgot",
    "how to solve {topic} problems",
    "5 rules of {topic} you must remember",
    "{topic} step by step worked example",
    "the intuition behind {topic}",
    "don't memorise {topic}, understand it",
    "{topic} from zero to concept",
    "one question a day: {topic}",
    "the examiner's favourite question on {topic}",
    "{topic} notes in 60 seconds",
    "shortcut for {topic} nobody taught you",
    "doubt clearing session on {topic}",
    "practical use of {topic} in real life",
    "{topic} full chapter revision one shot",
    "how i scored full marks using {topic}",
    "which is better in {topic}",
    "{topic} ke 3 important formulas",
    "{topic} ka derivation samjho",
    "{topic} — most repeated question in exams",
    "i wish someone taught me {topic} earlier",
]

NATURAL_ENT_TEMPLATES = [
    "wait for it {topic}",
    "POV: {topic}",
    "tag someone who does {topic}",
    "this {topic} went viral",
    "{topic} but it's funny",
    "nobody expected this {topic}",
    "{topic} at 3am",
    "{topic} full masti",
    "my {topic} obsession",
    "{topic} part 3",
    "unbelievable {topic} moment",
    "the {topic} edit that broke the internet",
    "when {topic} hits different",
    "{topic} best of 2026",
    "{topic} gone wrong",
    "{topic} compilation",
    "how i make {topic}",
    "{topic} challenge accepted",
    "epic {topic} fail",
    "reacting to {topic}",
    "nobody asked but {topic}",
    "this is why we love {topic}",
    "{topic} that everyone is watching",
    "found the best {topic} on the internet",
    "{topic} 😱😱",
    "{topic} in one go",
    "watch this {topic} till the end",
    "{topic} — had to share this",
]



# Exam/career notification content: "vacancy", "last date", "admit card", "result" are
# student material and must land in the academic class, not fall through as unknown words.
STUDY_GROUPS += [
    ("careers", "exam", [
        "government job vacancy", "last date to apply", "admit card download",
        "result date announcement", "counselling schedule", "eligibility criteria",
        "sarkari naukri notification", "exam centre list", "answer key release",
        "cut off marks", "scholarship application", "college admission form",
        "document verification", "age limit for exam", "application fee details",
    ], [
        "{topic} — official update",
        "{topic} full details",
        "{topic} for students",
        "{topic} explained in 60 seconds",
        "{topic} — last date reminder",
        "important: {topic}",
        "{topic} step by step process",
        "{topic} — what you must do today",
        "{topic} notification analysis",
        "{topic} update for aspirants",
    ]),
]

# --------------------------------------------------------------------------------------
# Second pass: coverage for the words that fooled the first model. Skill/study content
# that uses "entertainment" vocabulary (routine, song, edit, review, form) and
# entertainment that dresses up in study vocabulary (exam memes, "typing asmr").
# --------------------------------------------------------------------------------------

JUNK_GROUPS += [
    ("meme_bait", [
        "class 10 board exam meme", "homework meme", "maths exam meme",
        "teacher checking answer sheet", "physics teacher reaction",
        "when the teacher asks for homework", "student life meme",
        "attendance shortage meme", "viva exam meme", "result day reaction",
    ], [
        "{topic} 😂",
        "{topic} 😭😭",
        "{topic} part 2",
        "{topic} — every student will relate",
        "{topic} (must watch)",
        "{topic} ft. my class",
        "tag your friend who does {topic}",
        "{topic} | comedy",
    ]),
    ("asmr_tech", [
        "asmr keyboard typing", "asmr mechanical keyboard", "typing sounds for sleep",
        "asmr mouse clicking", "asmr writing with pen", "asmr page turning",
        "asmr laptop fan noise", "asmr calculator tapping",
    ], [
        "{topic} 😴",
        "{topic} — sleep in 5 minutes",
        "{topic} no talking",
        "{topic} 1 hour loop",
        "{topic} for studying (lol)",
        "{topic} asmr",
    ]),
    ("skill_bait", [
        "gym routine for beginners", "six pack ab routine", "full body workout plan",
        "my study routine in hostel", "everyday makeup routine",
    ], []),
]

# Topics for the informative side that use words the entertainment corpus also uses.
EXTRA_SKILL_TOPICS = [
    "gym routine", "home workout plan", "your first song on guitar", "editing reels",
    "review of a budget laptop", "review of a budget phone", "biomechanics of bowling",
    "deadlift setup cues", "guitar strumming pattern", "keyboard shortcuts for coding",
    "typing practice", "study routine that works", "morning routine for students",
    "meal plan for a week", "food labelling explained", "how to read a pay slip",
    "reading a job notification", "government exam eligibility", "how to file ITR",
    "understanding your salary structure", "phone specs explained",
    "benchmark vs real world performance", "comparison of two laptops",
    "what reviewers never tell you", "how to spot a fake review",
]
EXTRA_INFO_TOPICS = [
    "the economics of a movie ticket", "how sports analytics works",
    "the science of a football free kick", "cricket biomechanics",
    "why muscle growth happens", "protein and recovery science",
    "how a phone review is actually done", "what benchmark scores mean",
    "government schemes explained", "how to read a government notification",
    "the biology of sleep", "why typing speed matters at work",
    "how keyboards are manufactured", "the physics of a bicycle",
]


def _topic_split(topics, holdout, frac=0.15):
    """Deterministic topic-level train/holdout split (no topic appears in both)."""
    keep = []
    for t in topics:
        h = (sum(ord(c) for c in t) * 2654435761) % 1000
        is_holdout = h < frac * 1000
        if is_holdout == holdout:
            keep.append(t)
    return keep


def build(rng, n_target=26000, holdout=False, include_devanagari=True):
    """Generate the corpus. `holdout=True` produces the unseen-topic test set."""
    rows = []
    seen = set()

    def add(domain, title, channel, group):
        key = (title, channel)
        if key in seen:
            return False
        seen.add(key)
        rows.append({
            "title": title,
            "channel": channel,
            "domain": domain,
            "group": group,
            "informative": 0 if domain == "ent" else 1,
            "study": 1 if domain == "study" else 0,
        })
        return True

    # ---- informative groups
    for _, domain, topics, templates in STUDY_GROUPS:
        topics = _topic_split(topics, holdout)
        if not topics:
            continue
        per_group = n_target // (2 * len(STUDY_GROUPS))
        for _ in range(per_group):
            topic = rng.choice(topics)
            pool = templates + NATURAL_STUDY_TEMPLATES if rng.random() < 0.45 else templates
            tpl = rng.choice(pool)
            title = _decorate(rng, _typo(rng, tpl.format(topic=topic)), domain)
            if rng.random() < 0.06:
                # study content occasionally shows up on a channel that looks like junk
                channel = _mutate_channel(rng, rng.choice(JUNK_CHANNELS["ent"]))
            else:
                channel = _mutate_channel(rng,
                    rng.choice(INFORMATIVE_CHANNELS["study" if domain in ("study", "exam") else
                                               "info" if domain in ("science", "math", "bio", "med", "lang") else
                                               "skill" if domain == "cs" else "study"]))
            add(domain, title, channel, "study_group")

    # ---- informational explainers / news / finance / health
    INFO_TOPICS = [
        "the Indian economy", "inflation", "repo rate", "GDP growth", "budget 2026",
        "electric vehicles", "5G technology", "semiconductor chips", "quantum computing",
        "black holes", "James Webb telescope", "Chandrayaan mission", "Mars rover",
        "climate change", "carbon emissions", "solar power", "nuclear fusion",
        "vaccines", "antibiotic resistance", "sleep science", "microplastics",
        "artificial intelligence", "large language models", "cybersecurity",
        "data privacy", "blockchain", "UPI payments", "startup funding",
        "income tax slabs", "mutual funds", "SIP investing", "compound interest",
        "credit score", "insurance basics", "Indian Constitution", "fundamental rights",
        "Mughal history", "Indus Valley Civilisation", "freedom struggle",
        "world war 2", "cold war", "Silk Road", "geopolitics of oil",
        "monsoon system", "earthquakes", "ocean currents", "biodiversity loss",
    ]
    INFO_TEMPLATES = [
        "{topic} explained simply",
        "{topic} — what you should know",
        "{topic} in 60 seconds",
        "how {topic} works",
        "why {topic} matters in 2026",
        "the truth about {topic}",
        "{topic} explained with animation",
        "5 facts about {topic}",
        "{topic} — beginner friendly explainer",
        "the science behind {topic}",
        "{topic} analysis",
        "{topic} decoded",
        "what happens if {topic}",
        "misconception about {topic}",
        "{topic} — explained by a professor",
    ]
    info_topics = _topic_split(INFO_TOPICS + EXTRA_INFO_TOPICS, holdout)
    for _ in range(n_target // 10 if info_topics else 0):
        topic = rng.choice(info_topics)
        pool = INFO_TEMPLATES + NATURAL_STUDY_TEMPLATES if rng.random() < 0.45 else INFO_TEMPLATES
        title = _decorate(rng, _typo(rng, rng.choice(pool).format(topic=topic)), "info")
        channel = _mutate_channel(rng, rng.choice(INFORMATIVE_CHANNELS["info"]))
        add("info", title, channel, "info_group")

    # ---- skill
    SKILL_TOPICS = [
        "boiling the perfect egg", "kneading dough", "knife skills", "making white sauce",
        "dum biryani", "paneer butter masala", "chocolate cake", "sourdough starter",
        "push up form", "deadlift setup", "squat depth", "hip mobility stretch",
        "beginner guitar chords", "barre chord", "strumming pattern", "piano scales",
        "photo composition", "manual mode camera", "lightroom editing", "portrait lighting",
        "excel vlookup", "pivot tables", "conditional formatting", "google sheets formulas",
        "resume formatting", "linkedin profile", "cover letter", "interview answers",
        "saving money monthly", "budget planning", "fixing a leaking tap", "wall drilling",
        "typing speed", "first aid for cuts", "plant care", "car maintenance basics",
    ]
    SKILL_TEMPLATES = [
        "{topic} tutorial",
        "{topic} for beginners",
        "{topic} — step by step",
        "{topic} tips that actually work",
        "{topic} in 60 seconds",
        "the right way to do {topic}",
        "{topic} mistakes to avoid",
        "{topic} masterclass clip",
        "3 hacks for {topic}",
        "{topic} explained by a pro",
    ]
    skill_topics = _topic_split(SKILL_TOPICS + EXTRA_SKILL_TOPICS, holdout)
    for _ in range(n_target // 16 if skill_topics else 0):
        topic = rng.choice(skill_topics)
        pool = SKILL_TEMPLATES + NATURAL_STUDY_TEMPLATES if rng.random() < 0.45 else SKILL_TEMPLATES
        title = _decorate(rng, _typo(rng, rng.choice(pool).format(topic=topic)), "skill")
        channel = _mutate_channel(rng, rng.choice(INFORMATIVE_CHANNELS["skill"]))
        add("skill", title, channel, "skill_group")

    # ---- junk groups (titles and channels both spliced: topics are the content words)
    for _, topics, templates in JUNK_GROUPS:
        topics = _topic_split(topics, holdout)
        if not topics:
            continue
        per_group = n_target // (2 * len(JUNK_GROUPS))
        for _ in range(per_group):
            if not topics or not templates:
                break
            topic = rng.choice(topics)
            pool = templates + NATURAL_ENT_TEMPLATES if rng.random() < 0.45 else templates
            title = _decorate(rng, _typo(rng, rng.choice(pool).format(topic=topic)), "ent")
            # a slice of junk comes from channels that look educational (and vice versa
            # for the study groups) so the model cannot just memorise channel names
            if rng.random() < 0.06 and INFORMATIVE_CHANNELS["study"]:
                channel = _mutate_channel(rng, rng.choice(INFORMATIVE_CHANNELS["study"]))
            else:
                channel = _mutate_channel(rng, rng.choice(JUNK_CHANNELS["ent"]))
            add("ent", title, channel, "junk_group")

    # ---- Devanagari slice (split by title hash so train/test stay disjoint)
    dev = augment_devanagari(rng, 1500 if not holdout else 350)
    for r in dev:
        if holdout != ((sum(ord(c) for c in r["title"]) % 100) < 15):
            continue
        add(r["domain"], r["title"], r["channel"], r["group"])

    rng.shuffle(rows)
    return rows



if __name__ == "__main__":
    import json
    rng = random.Random(7)
    rows = build(rng)
    print(json.dumps(rows[:5], indent=2))
    print("total", len(rows))
    from collections import Counter
    print(Counter(r["domain"] for r in rows))
