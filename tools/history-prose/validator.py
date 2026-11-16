"""Checks a generated text against its fact sheet.

Every proper name and every number in the text must come from the sheet. A name passes when the whole
capitalised phrase occurs in some string of the sheet. A number passes when the sheet holds it anywhere.
For a German translation, the check is different (German capitalises every noun): the invented words of
the English text must survive unchanged, and so must its numbers.
"""
import json
import re

ENGLISH_WORDS = set()
try:
    with open("/usr/share/dict/american-english", encoding="utf-8") as f:
        ENGLISH_WORDS = {w.strip().lower() for w in f if w.strip().isalpha()}
except FileNotFoundError:
    pass

# Capitalised words that are style, not names: hymns address a god, and a sentence starts somewhere.
STYLE_WORDS = {
    "I", "O", "Oh", "Thou", "Thee", "Thy", "Thine", "Ye", "He", "She", "They", "His", "Her", "Their",
    "Lord", "Lady", "Mother", "Father", "Amen", "Here", "Hail", "Glory", "Twas", "Tis",
}
# Generic titles a model likes to put before a name. Kept apart so the report can count them.
TITLES = {"King", "Queen", "Lord", "Lady", "Prince", "Princess", "General", "Elder", "Saint", "Master", "Sir"}

CONNECTORS = {"of", "the"}
WORD = r"[A-Z][\w'’-]*"
# Spaces only, never a line break: a grave stone puts a name on one line and "Born" on the next.
PHRASE = re.compile(rf"{WORD}(?:[ \t]+(?:of[ \t]+the|of|the)[ \t]+{WORD}|[ \t]+{WORD})*")
NUMBER = re.compile(r"(?<![\w.])\d[\d,]*(?![\w.]\d)")


def sheet_strings(sheet):
    out = []

    def walk(value):
        if isinstance(value, dict):
            for v in value.values():
                walk(v)
        elif isinstance(value, list):
            for v in value:
                walk(v)
        elif isinstance(value, str):
            out.append(value)
        elif isinstance(value, int):
            out.append(str(value))

    walk(sheet)
    return out


def sheet_numbers(sheet):
    numbers = set()
    for s in sheet_strings(sheet):
        for n in NUMBER.findall(s):
            numbers.add(int(n.replace(",", "")))
    return numbers


def _sentence_starts(text):
    starts = {0}
    for m in re.finditer(r"[.!?:;\n\"“]\s*", text):
        starts.add(m.end())
    return starts


def _strip_possessive(word):
    return re.sub(r"['’]s$", "", word)


def check_english(text, sheet):
    """Returns a list of problems. Empty means the text passes."""
    haystack = "\n".join(sheet_strings(sheet))
    sheet_words = set(re.findall(r"[\w'-]+", haystack))
    starts = _sentence_starts(text)
    problems = []

    for m in PHRASE.finditer(text):
        phrase = _strip_possessive(m.group(0).strip())
        words = phrase.split()
        at_start = m.start() in starts

        # Drop style words and a sentence-initial common word, then check what is left.
        while words and (words[0] in STYLE_WORDS or (at_start and words[0].lower() in ENGLISH_WORDS
                                                      and words[0] not in sheet_words)):
            words = words[1:]
            at_start = False
        while words and words[0] in CONNECTORS:
            words = words[1:]
        if not words:
            continue
        title = None
        if words[0] in TITLES and len(words) > 1:
            title = words[0]
            words = words[1:]
        name = " ".join(words)
        if name in haystack:
            if title:
                problems.append({"type": "title", "value": f"{title} {name}"})
            continue
        # A capital that is only a common word, like "Weaver" alone, passes when the sheet has it.
        if len(words) == 1 and name in sheet_words:
            continue
        # "Temujin of Karaobo": two sheet names joined. Whether he really came from there is not checked here.
        parts = re.split(r" of (?:the )?", name)
        if len(parts) > 1 and all(part in haystack for part in parts):
            continue
        problems.append({"type": "name", "value": phrase})

    problems += _wrong_case(text, haystack)

    allowed = sheet_numbers(sheet)
    for n in NUMBER.findall(text):
        value = int(n.replace(",", ""))
        if value not in allowed:
            problems.append({"type": "number", "value": n})
    for words, value in spelled_numbers(text):
        # Small counts ("the two peoples") are style. A count of dead or a span of years is a fact.
        if value > SMALL_COUNT and value not in allowed:
            problems.append({"type": "number", "value": words})

    lowercase = [m.group(1) for m in re.finditer(r"(?:^|[.!?]\s+)([a-z]\w*)", text)]
    if lowercase:
        problems.append({"type": "lowercase", "value": lowercase[0]})
    return problems


SMALL_COUNT = 12
UNITS = {w: i for i, w in enumerate(
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen "
    "seventeen eighteen nineteen".split())}
TENS = {w: 10 * (i + 2) for i, w in enumerate("twenty thirty forty fifty sixty seventy eighty ninety".split())}
SCALES = {"hundred": 100, "thousand": 1000}
NUMBER_WORD = "|".join(list(UNITS) + list(TENS) + list(SCALES))
SPELLED = re.compile(rf"\b(?:{NUMBER_WORD})(?:[\s-]+(?:and[\s-]+)?(?:{NUMBER_WORD}))*\b", re.IGNORECASE)


def spelled_numbers(text):
    """Numbers written as words, with their value: "four hundred and eight" is 408."""
    out = []
    for m in SPELLED.finditer(text):
        for phrase in _split_on_and(m.group(0)):
            out.append((phrase, _value_of(phrase.lower().replace("-", " ").split())))
    return out


def _split_on_and(phrase):
    """ "four hundred and eight" is one number; "five seventy-one and six thirty-six" are two."""
    parts, current = [], []
    for word in phrase.split():
        if word.lower() == "and" and current and current[-1].lower() not in SCALES:
            parts.append(" ".join(current))
            current = []
        else:
            current.append(word)
    if current:
        parts.append(" ".join(current))
    return parts


def _value_of(words):
    words = [w for w in words if w != "and"]
    # A year said aloud: "five seventy-one" is 571, not 76.
    if len(words) >= 2 and words[0] in UNITS and words[1] in TENS and not set(words) & set(SCALES):
        return UNITS[words[0]] * 100 + sum(UNITS.get(w, 0) + TENS.get(w, 0) for w in words[1:])
    total, current = 0, 0
    for word in words:
        if word in UNITS:
            current += UNITS[word]
        elif word in TENS:
            current += TENS[word]
        elif word == "hundred":
            current = max(current, 1) * 100
        elif word == "thousand":
            total += max(current, 1) * 1000
            current = 0
    return total + current


def _wrong_case(text, haystack):
    """A sheet name written in other letter case: "keshband" for "Keshband". Small models do this."""
    names = {w for w in re.findall(rf"{WORD}", haystack) if w.lower() not in ENGLISH_WORDS}
    problems = []
    for name in sorted(names):
        for m in re.finditer(rf"\b{re.escape(name)}\b", text, flags=re.IGNORECASE):
            if m.group(0) != name:
                problems.append({"type": "case", "value": m.group(0)})
                break
    return problems


def invented_words(text):
    """Capitalised words no English dictionary knows: the names a translation must keep."""
    words = set()
    for w in re.findall(rf"{WORD}", text):
        w = _strip_possessive(w)
        if w.lower() not in ENGLISH_WORDS and w not in STYLE_WORDS:
            words.add(w)
    return words


def check_translation(source, target):
    problems = []
    for w in sorted(invented_words(source)):
        if w not in target:
            problems.append({"type": "lost-name", "value": w})
    src_numbers = sorted(NUMBER.findall(source))
    dst_numbers = sorted(n.replace(".", ",") for n in NUMBER.findall(target))
    for n in src_numbers:
        if n not in dst_numbers and n.replace(",", "") not in [d.replace(",", "") for d in dst_numbers]:
            problems.append({"type": "lost-number", "value": n})
    return problems


def feedback(problems):
    """One sentence per problem, for the retry prompt."""
    lines = []
    for p in problems:
        if p["type"] == "name":
            lines.append(f'"{p["value"]}" is not a name in the fact sheet. Use only names from the sheet.')
        elif p["type"] == "title":
            lines.append(f'Do not add the title in "{p["value"]}". Write the name exactly as the sheet does.')
        elif p["type"] == "case":
            lines.append(f'Write "{p["value"]}" with capital letters, exactly as the fact sheet does.')
        elif p["type"] == "lowercase":
            lines.append(f'A sentence starts with "{p["value"]}". Start every sentence with a capital letter.')
        elif p["type"] == "number":
            lines.append(f'The number {p["value"]} is not in the fact sheet. Use only years and numbers from the sheet.')
    return " ".join(lines)


if __name__ == "__main__":
    import sys
    sheet = json.loads(sys.argv[1])
    print(check_english(sys.stdin.read(), sheet))
