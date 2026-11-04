# -*- coding: utf-8 -*-
"""English image prompts per card id (category → id → prompt). The common style is added by gen.py.
People are Iranian, modestly dressed; adult women wear a loose headscarf. One clear subject per card."""

BOY = "a cheerful Iranian boy about 5 years old with short dark brown hair"
GIRL = "a cheerful Iranian girl about 5 years old with dark hair tied back"
KID = lambda i: BOY if i % 2 == 0 else GIRL

VERBS = {
    "run": "{k} running happily, full body, side view", "walk": "{k} walking, full body", "swim": "{k} swimming in a pool with goggles",
    "bike": "{k} riding a small bicycle with a helmet", "climb": "{k} climbing a playground climbing wall", "stand": "{k} standing straight, full body, arms by the sides",
    "kneel": "{k} kneeling on one knee on the floor", "sleep": "{k} sleeping in bed under a blanket, eyes closed", "bath": "{k} taking a bath in a bathtub with bubbles",
    "hug": "an Iranian mother in a headscarf hugging her young son", "dance": "{k} dancing joyfully", "ball": "{k} playing with a red ball",
    "exercise": "{k} doing a simple stretching exercise", "raise": "{k} raising one hand in a classroom", "bow": "{k} greeting politely with a hand on the chest",
    "no": "{k} shaking head and gesturing no with a hand", "ok": "{k} nodding and giving a thumbs up", "eat": "{k} eating rice from a white bowl with a spoon at a wooden table",
    "drink": "{k} drinking water from a glass", "laugh": "{k} laughing out loud", "cry": "{k} crying with tears",
    "yawn": "{k} yawning with a hand over the mouth", "sneeze": "{k} sneezing into a tissue", "think": "{k} thinking with a finger on the chin, looking up",
    "listen": "{k} listening carefully with a hand behind the ear", "look": "{k} looking through binoculars", "talk": "two children talking to each other face to face",
    "write": "{k} writing in a notebook with a pencil at a desk", "paint": "{k} painting a picture with a brush on paper", "read": "{k} reading a picture book",
    "sing": "{k} singing with mouth open and musical notes around", "clap": "{k} clapping hands happily", "wave": "{k} waving goodbye with one hand",
    "shake": "two children shaking hands", "brush": "{k} brushing teeth with a toothbrush", "wash": "{k} washing hands with soap at a sink",
    "kiss": "an Iranian mother in a headscarf kissing her child's cheek", "cook": "{k} helping cook, stirring a pot with a wooden spoon", "call": "{k} talking on a phone",
    "wait": "{k} sitting patiently on a chair waiting", "sweep": "{k} sweeping the floor with a broom", "shop": "{k} pushing a small shopping cart in a store",
    "puzzle": "{k} doing a jigsaw puzzle on the floor", "feed": "an Iranian mother in a headscarf feeding a baby with a spoon", "haircut": "{k} getting a haircut at a barber",
    "row": "{k} rowing a small boat on a lake", "toilet": "{k} sitting on a child toilet seat in a clean bathroom", "dress": "{k} putting on a t-shirt",
}

FEELINGS = {
    "happy": "happy smiling", "very_happy": "very happy with a big open smile", "sad": "sad with a frown", "crying": "crying with tears",
    "angry": "angry with furrowed eyebrows", "furious": "very angry, red face, clenched fists", "scared": "scared and worried, hands near face",
    "terrified": "terrified, screaming", "surprised": "surprised with open mouth and wide eyes", "tired": "tired and exhausted",
    "sleepy": "sleepy, rubbing eyes", "asleep": "asleep with eyes closed, head on a pillow", "sick": "sick with a thermometer in mouth",
    "hurt": "with a bandage on the forehead, a little hurt", "nauseous": "feeling sick to the stomach, green face, hand on belly",
    "cold": "shivering from cold wearing a scarf", "hot": "hot and sweating, fanning the face", "calm": "calm and relaxed with closed eyes, peaceful smile",
    "shy": "shy, blushing, looking down", "worried": "worried and nervous", "confused": "confused, scratching head with question marks",
    "excited": "excited, jumping with joy", "love": "hugging a teddy bear lovingly with little hearts", "bored": "bored, chin resting on hand",
    "cool": "confident and proud with hands on hips", "quiet": "with a finger on lips saying shh", "hungry": "hungry, holding belly, looking at food",
    "curious": "curious, looking at a butterfly with a magnifying glass", "party": "celebrating at a birthday party with a party hat",
    "pain": "in pain, holding the tummy", "please": "asking politely with hands together", "proud": "proud holding a gold medal",
}

JOBS = {
    "doctor": "an Iranian male doctor in a white coat with a stethoscope", "teacher": "an Iranian female teacher in a headscarf at a whiteboard",
    "chef": "an Iranian chef in a white chef hat holding a ladle", "firefighter": "a firefighter in uniform and helmet with a hose",
    "police": "an Iranian police officer in uniform", "farmer": "an Iranian farmer with a straw hat holding vegetables in a field",
    "mechanic": "a mechanic in overalls holding a wrench next to a car", "scientist": "a scientist in a lab coat with a test tube",
    "programmer": "a person working on a laptop at a desk", "pilot": "an airline pilot in uniform with a cap", "astronaut": "an astronaut in a white space suit",
    "artist": "a painter with a palette at an easel", "singer": "a male singer holding a microphone", "builder": "a construction worker with a yellow hard hat",
    "guard": "a security guard in uniform", "detective": "a detective with a magnifying glass", "judge": "a judge in a black robe with a gavel",
    "office": "an office worker in a suit at a desk", "factory": "a factory worker with safety goggles", "student": "an Iranian schoolboy with a backpack",
    "nurse": "an Iranian female nurse in a headscarf and uniform", "dentist": "a dentist holding a toothbrush and a tooth model",
    "baker": "a baker taking fresh flat bread (barbari) out of an oven", "driver": "a taxi driver at the steering wheel", "postman": "a mail carrier holding letters",
    "barber": "a barber with scissors and comb", "tailor": "a tailor sewing with a sewing machine",
}

PEOPLE = {
    "father": "an Iranian father, friendly smiling man with short dark hair and a short beard, upper body", "mother": "an Iranian mother wearing a loose headscarf, warm smile, upper body",
    "grandpa": "an Iranian grandfather with white hair and glasses, smiling", "grandma": "an Iranian grandmother in a white headscarf, smiling",
    "boy": BOY + ", upper body", "girl": GIRL + ", upper body", "baby": "a cute baby sitting and smiling", "family": "an Iranian family: father, mother in a headscarf, a boy and a girl together, smiling",
    "friends": "two children friends holding hands and smiling", "person": "a friendly young adult, upper body", "wheelchair": "a smiling child in a wheelchair",
    "deaf": "a child wearing a hearing aid, smiling", "blind": "an adult walking with a white cane and dark glasses", "bearded": "a man with a full dark beard",
    "brother": "an older brother holding his little brother's hand", "sister": "two sisters smiling together", "teacher_p": "a kind school teacher (woman in a headscarf) reading to a child",
}

ANIMALS = {k: v for k, v in [
    ("dog", "a friendly dog"), ("cat", "a cute cat"), ("mouse", "a little mouse"), ("rabbit", "a white rabbit"), ("fox", "a red fox"), ("bear", "a brown bear"),
    ("panda", "a panda"), ("koala", "a koala on a branch"), ("tiger", "a tiger"), ("lion", "a lion with a mane"), ("cow", "a cow"), ("horse", "a horse"),
    ("sheep", "a sheep"), ("goat", "a goat"), ("camel", "a camel"), ("elephant", "an elephant"), ("giraffe", "a giraffe"), ("zebra", "a zebra"),
    ("monkey", "a monkey"), ("gorilla", "a gorilla"), ("wolf", "a wolf"), ("deer", "a deer"), ("squirrel", "a squirrel holding a nut"), ("hedgehog", "a hedgehog"),
    ("kangaroo", "a kangaroo"), ("hippo", "a hippopotamus"), ("rhino", "a rhinoceros"), ("crocodile", "a crocodile"), ("turtle", "a turtle"), ("snake", "a green snake"),
    ("frog", "a green frog"), ("chicken", "a hen"), ("rooster", "a rooster"), ("chick", "a yellow chick"), ("duck", "a duck"), ("bird", "a small sparrow"),
    ("parrot", "a colorful parrot"), ("owl", "an owl"), ("eagle", "an eagle"), ("peacock", "a peacock with open tail"), ("penguin", "a penguin"), ("fish", "a goldfish"),
    ("dolphin", "a dolphin"), ("whale", "a blue whale"), ("shark", "a shark"), ("octopus", "an octopus"), ("crab", "a crab"), ("bee", "a honeybee"),
    ("butterfly", "a butterfly"), ("ladybug", "a ladybug"), ("snail", "a snail"), ("ant", "an ant"), ("spider", "a spider"),
]}

OBJECTS = {k: v for k, v in [
    ("ball", "a soccer ball"), ("book", "a closed children's book"), ("pencil", "a yellow pencil"), ("crayon", "colored crayons"), ("scissors", "a pair of child safety scissors"),
    ("ruler", "a ruler"), ("backpack", "a school backpack"), ("clock", "an alarm clock"), ("watch", "a wristwatch"), ("phone", "a smartphone"), ("key", "a key"),
    ("chair", "a wooden chair"), ("bed", "a child's bed"), ("door", "a wooden door"), ("glasses", "eyeglasses"), ("umbrella", "an open umbrella"),
    ("toothbrush", "a toothbrush with toothpaste"), ("soap", "a bar of soap with bubbles"), ("cup", "a cup"), ("spoon", "a spoon"), ("fork", "a fork"),
    ("plate", "an empty plate"), ("bulb", "a light bulb"), ("tv", "a television"), ("laptop", "a laptop computer"), ("gift", "a wrapped gift box with a bow"),
    ("balloon", "a red balloon"), ("teddy", "a teddy bear"), ("broom", "a broom"), ("bin", "a trash bin"), ("mirror", "a round mirror"), ("puzzle", "a jigsaw puzzle piece"),
    ("hammer", "a hammer"), ("candle", "a lit candle"), ("box", "a cardboard box"), ("letter", "an envelope"), ("brush", "a paintbrush"), ("camera", "a camera"),
    ("headphone", "headphones"), ("cart", "a shopping cart"), ("bathtub", "a bathtub"), ("toilet", "a toilet"), ("shower", "a shower head with water"),
    ("house", "a house"), ("tree", "a green tree"), ("flower", "a flower"), ("sun", "the sun"), ("moon", "a crescent moon"), ("star", "a yellow star"),
    ("cloud", "a white cloud"), ("rain", "a cloud with raindrops"), ("snow", "snowflakes falling"), ("fire", "a campfire"),
    ("towel", "a folded towel"), ("comb", "a comb"), ("table", "a wooden table"), ("window", "a window"), ("pillow", "a pillow"), ("blanket", "a folded blanket"),
]}

FOOD = {k: v for k, v in [
    ("apple", "a red apple"), ("banana", "a banana"), ("orange", "an orange"), ("grapes", "a bunch of grapes"), ("watermelon", "a slice of watermelon"),
    ("strawberry", "a strawberry"), ("cherries", "cherries"), ("peach", "a peach"), ("pear", "a pear"), ("lemon", "a lemon"), ("pineapple", "a pineapple"),
    ("pomegranate", "a pomegranate cut open"), ("dates", "dates on a small plate"), ("carrot", "a carrot"), ("tomato", "a tomato"), ("cucumber", "a cucumber"),
    ("potato", "a potato"), ("corn", "an ear of corn"), ("onion", "an onion"), ("pepper", "a green bell pepper"), ("lettuce", "a lettuce"), ("mushroom", "a mushroom"),
    ("bread", "Iranian flat bread (sangak)"), ("cheese", "a piece of white feta cheese"), ("egg", "a boiled egg"), ("milk", "a glass of milk"),
    ("water", "a glass of water"), ("rice", "a plate of Persian white rice"), ("soup", "a bowl of soup"), ("chicken_leg", "a roasted chicken drumstick"),
    ("tea", "a glass of Persian tea"), ("honey", "a jar of honey"), ("cake", "a slice of cake"), ("icecream", "an ice cream cone"), ("chocolate", "a chocolate bar"),
    ("candy", "a wrapped candy"), ("cookie", "a cookie"), ("pizza", "a slice of pizza"), ("sandwich", "a sandwich"), ("yogurt", "a bowl of yogurt"),
    ("kebab", "Persian kebab on a skewer with rice"), ("juice", "a glass of orange juice"),
]}

VEHICLES = {k: v for k, v in [
    ("car", "a small family car"), ("bus", "a city bus"), ("taxi", "a yellow taxi"), ("ambulance", "an ambulance"), ("firetruck", "a red fire truck"),
    ("policecar", "a police car"), ("truck", "a truck"), ("tractor", "a tractor"), ("train", "a train"), ("metro", "a metro subway train"),
    ("airplane", "an airplane"), ("helicopter", "a helicopter"), ("ship", "a ship"), ("sailboat", "a sailboat"), ("bicycle", "a bicycle"),
    ("motorcycle", "a motorcycle"), ("scooter", "a kick scooter"), ("rocket", "a rocket"),
]}

CLOTHES = {k: v for k, v in [
    ("tshirt", "a t-shirt"), ("jeans", "a pair of jeans"), ("dress", "a girl's dress"), ("socks", "a pair of socks"), ("sneaker", "sneakers"),
    ("shoe", "leather shoes"), ("boot", "rain boots"), ("cap", "a baseball cap"), ("gloves", "a pair of gloves"), ("scarf", "a winter scarf"),
    ("coat", "a winter coat"), ("sunglasses", "sunglasses"), ("pajamas", "children's pajamas"), ("sandal", "slippers"), ("shorts", "shorts"),
    ("headscarf", "a folded headscarf"), ("hat", "a warm knitted hat"),
]}

BODY = {k: "a simple friendly child illustration of a " + v for k, v in [
    ("eye", "child's eye, close-up"), ("ear", "child's ear, close-up"), ("nose", "child's nose, close-up face"), ("mouth", "child's smiling mouth"),
    ("tongue", "child sticking out the tongue"), ("tooth", "healthy white tooth, cartoon"), ("hand", "child's open hand"), ("foot", "child's bare foot"),
    ("leg", "child's leg"), ("arm", "child's arm showing a muscle"), ("hair", "child's head of hair"), ("head", "child's head"), ("belly", "child pointing at the tummy"),
    ("knee", "child's knee"), ("finger", "child's finger pointing"),
]}

COLORS = {k: v for k, v in [
    ("red", "a red paint splash and a red apple"), ("orange_c", "an orange paint splash"), ("yellow", "a yellow paint splash and a yellow duck toy"),
    ("green", "a green paint splash and a green leaf"), ("blue", "a blue paint splash"), ("purple", "a purple paint splash"), ("brown", "a brown paint splash"),
    ("black", "a black paint splash"), ("white", "a white fluffy cloud on light blue"), ("pink", "a pink paint splash"), ("gray", "a gray paint splash"),
    ("circle", "a red circle shape, flat"), ("square", "a blue square shape, flat"), ("triangle", "a green triangle shape, flat"),
    ("rectangle", "an orange rectangle shape, flat"), ("heart_s", "a red heart shape, flat"), ("star_s", "a yellow star shape, flat"),
]}

NUMBERS = {("n%d" % n): ("exactly %d red apple%s arranged neatly in a row, nothing else" % (n, "" if n == 1 else "s")) if n else "an empty plate, nothing on it"
           for n in range(0, 11)}

CATS = [("verbs", VERBS), ("feelings", FEELINGS), ("jobs", JOBS), ("people", PEOPLE), ("animals", ANIMALS), ("objects", OBJECTS),
        ("food", FOOD), ("vehicles", VEHICLES), ("clothes", CLOTHES), ("body", BODY), ("colors", COLORS), ("numbers", NUMBERS)]


def prompt_for(cat, cid, i):
    p = dict(CATS)[cat][cid]
    if cat == "verbs":
        p = p.format(k=KID(i))
    elif cat == "feelings":
        p = "close-up of %s, face and shoulders, clearly %s" % (KID(i).replace("a cheerful ", "an "), p)
    elif cat == "animals":
        p = p + ", cute and friendly, full body"
    return p
