"""Builds the job lists: the reading set (a few rich entities), and every text one world needs."""
import argparse
import json


def load(path):
    return [json.loads(line) for line in open(path, encoding="utf-8")]


def of_kind(sheets, kind):
    return [s for s in sheets if s["kind"] == kind]


def richness(sheet):
    return len(sheet.get("events", [])) + len(sheet.get("ties", [])) + len(sheet.get("names", []))


def war_jobs(war):
    a, b = (side["people"] for side in war["sides"])
    return [
        {"ref": war["ref"], "type": "book", "args": {"side": a, "other": b}},
        {"ref": war["ref"], "type": "book", "args": {"side": b, "other": a}},
    ]


def hymn_job(deity):
    singers = deity.get("patronOf") or [deity["namesByPeople"][0]["people"]]
    people = singers[0]
    name = next(n["name"] for n in deity["namesByPeople"] if n["people"] == people)
    return {"ref": deity["ref"], "type": "hymn", "args": {"name": name, "domain": deity["domain"], "people": people}}


def reading_set(sheets):
    war = max(of_kind(sheets, "war"), key=lambda w: (len(w["events"]), w["declared"]))
    buried = [s for s in of_kind(sheets, "figure") if s.get("grave")]
    # Five graves of five different roles where possible, richest first.
    epitaphs, roles = [], set()
    for s in sorted(buried, key=richness, reverse=True):
        if s["role"] not in roles or len(roles) >= 5:
            epitaphs.append(s)
            roles.add(s["role"])
        if len(epitaphs) == 5:
            break
    legends, forms = [], set()
    for s in sorted(of_kind(sheets, "artifact"), key=lambda a: len(a["events"]), reverse=True):
        if s["form"] not in forms:
            legends.append(s)
            forms.add(s["form"])
        if len(legends) == 3:
            break
    hymns = sorted(of_kind(sheets, "deity"), key=richness, reverse=True)[:2]
    return (
        war_jobs(war)
        + [{"ref": s["ref"], "type": "epitaph"} for s in epitaphs]
        + [{"ref": s["ref"], "type": "legend"} for s in legends]
        + [hymn_job(s) for s in hymns]
    )


def whole_world(sheets):
    jobs = [{"ref": s["ref"], "type": "epitaph"} for s in of_kind(sheets, "figure") if s.get("grave")]
    jobs += [{"ref": s["ref"], "type": "legend"} for s in of_kind(sheets, "artifact")]
    jobs += [hymn_job(s) for s in of_kind(sheets, "deity")]
    for war in of_kind(sheets, "war"):
        jobs += war_jobs(war)
    jobs += [{"ref": s["ref"], "type": "people"} for s in of_kind(sheets, "civ")]
    jobs += [{"ref": s["ref"], "type": "memorial"} for s in of_kind(sheets, "battle")]
    jobs += [{"ref": s["ref"], "type": "tract"} for s in of_kind(sheets, "cult")]
    jobs += [{"ref": s["ref"], "type": "beast"} for s in of_kind(sheets, "monster")]
    jobs += [{"ref": s["ref"], "type": "house"} for s in of_kind(sheets, "dynasty")]
    return jobs


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("sheets")
    ap.add_argument("--world", action="store_true", help="every text the world needs, not the reading set")
    args = ap.parse_args()
    sheets = load(args.sheets)
    print(json.dumps(whole_world(sheets) if args.world else reading_set(sheets), indent=1))
