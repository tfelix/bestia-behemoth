class_name SensedMarks
extends RefCounted

## What Resource Sense and Echo Sounding found, held until it fades. Each mark points at a spot in map metres; the
## compass and the chat show it, and nothing is put on the map for good.

## The proto's SenseKind, in its order.
enum Kind { ORE, HERBS, TIMBER, GAME, HOLLOW, MANA, BINDING_FIRED, EMPYRITE_SEED_FOUND, EMPYRITE_SEED, OMEN, EVENT }

const _COLOURS := {
	Kind.ORE: Color(0.85, 0.62, 0.35),
	Kind.HERBS: Color(0.45, 0.85, 0.45),
	Kind.TIMBER: Color(0.30, 0.62, 0.30),
	Kind.GAME: Color(0.95, 0.55, 0.45),
	Kind.HOLLOW: Color(0.65, 0.65, 0.85),
	Kind.MANA: Color(0.72, 0.52, 1.0),
	Kind.BINDING_FIRED: Color(1.0, 0.45, 0.85),
	Kind.EMPYRITE_SEED_FOUND: Color(1.0, 0.8, 0.16),
	Kind.EMPYRITE_SEED: Color(1.0, 0.8, 0.16),
	Kind.OMEN: Color(0.62, 0.45, 1.0),
	Kind.EVENT: Color(1.0, 0.55, 0.85),
}

## How far an Observation mark may be shown: the proto's size_metres for SENSE_EVENT.
enum Precision { DIRECTION, NEAR, FAR, DISTANCE }

## Each entry: the mark's dictionary plus [code]until[/code], in ticks msec.
var _marks: Array[Dictionary] = []


## Replaces what the same kinds of sense showed before: a new cast is a new reading, not an addition.
func add(message: SenseMarksSMSG) -> void:
	var until := Time.get_ticks_msec() + message.VisibleSeconds * 1000
	var kinds := {}
	for mark in message.Marks:
		kinds[int(mark["kind"])] = true
	_marks = _marks.filter(func(it: Dictionary) -> bool: return not kinds.has(int(it["kind"])))
	for mark in message.Marks:
		var held: Dictionary = mark.duplicate()
		held["until"] = until
		_marks.append(held)


## Bearing marks for the compass from [param here], in map metres. Drops what has faded.
func compass_marks(here: Vector2) -> Array:
	var now := Time.get_ticks_msec()
	_marks = _marks.filter(func(it: Dictionary) -> bool: return int(it["until"]) > now)

	var result: Array = []
	for mark in _marks:
		if bool(mark.get("vague", false)):
			continue
		var to := Vector2(float(mark["x"]), float(mark["y"]))
		result.append({
			"bearing": CompassStrip.bearing_between(Vector3(here.x, 0.0, here.y), Vector3(to.x, 0.0, to.y)),
			"colour": _COLOURS.get(int(mark["kind"]), Color.WHITE),
			"label": "%s %s" % [name_of(mark), _distance_of(mark, here.distance_to(to))],
		})
	return result


## One chat line for a reading, as seen from [param here].
static func summary(message: SenseMarksSMSG, here: Vector2) -> String:
	if message.Marks.is_empty():
		return tr_static("SENSE_NOTHING")
	if int(message.Marks[0]["kind"]) == Kind.BINDING_FIRED:
		return tr_static("SPELL_BINDING_FIRED") % str(message.Marks[0]["label"])
	if int(message.Marks[0]["kind"]) == Kind.EMPYRITE_SEED_FOUND:
		return tr_static("EMPYRITE_SEED_FOUND") % str(message.Marks[0]["label"])
	if int(message.Marks[0]["kind"]) == Kind.EVENT:
		var events: Array[String] = []
		for mark in message.Marks:
			var to := Vector2(float(mark["x"]), float(mark["y"]))
			var bearing := CompassStrip.bearing_between(Vector3(here.x, 0.0, here.y), Vector3(to.x, 0.0, to.y))
			events.append("%s %s %s" % [name_of(mark), CompassStrip.name_of(bearing), _distance_of(mark, here.distance_to(to))])
		return tr_static("SENSE_EVENT_READING") % ", ".join(events)
	if int(message.Marks[0]["kind"]) == Kind.OMEN:
		var readings: Array[String] = []
		for mark in message.Marks:
			readings.append(name_of(mark))
		return tr_static("SENSE_OMEN_READING") % ", ".join(readings)

	var vague := message.Marks.filter(func(it: Dictionary) -> bool: return bool(it.get("vague", false)))
	if not vague.is_empty():
		return tr_static("SENSE_MANA_NEARBY") % vague.size()

	var parts: Array[String] = []
	for mark in message.Marks:
		var to := Vector2(float(mark["x"]), float(mark["y"]))
		var bearing := CompassStrip.bearing_between(Vector3(here.x, 0.0, here.y), Vector3(to.x, 0.0, to.y))
		var part := "%s %s %.1fkm" % [name_of(mark), CompassStrip.name_of(bearing), here.distance_to(to) / 1000.0]
		if float(mark["depth"]) > 0.0:
			part += " " + tr_static("SENSE_HOLLOW_DETAIL") % [roundi(float(mark["depth"])), roundi(float(mark["size"]))]
		parts.append(part)
	return tr_static("SENSE_FOUND") % ", ".join(parts)


static func name_of(mark: Dictionary) -> String:
	if int(mark["kind"]) == Kind.GAME:
		var species := BestiaDB.get_instance().get_bestia(int(mark["ref_id"]))
		return str(TranslationServer.translate(species.name_key)) if species != null else "?"
	if int(mark["kind"]) == Kind.HOLLOW:
		return tr_static("SENSE_HOLLOW")
	if int(mark["kind"]) == Kind.MANA:
		return tr_static("SENSE_MANA") % str(mark["label"])
	if int(mark["kind"]) == Kind.EVENT:
		return _event_name(mark)
	if int(mark["kind"]) == Kind.OMEN:
		var reading := tr_static("SENSE_OMEN_%d" % int(mark["ref_id"]))
		return reading if str(mark["label"]).is_empty() else "%s: %s" % [str(mark["label"]), reading]
	if int(mark["kind"]) == Kind.EMPYRITE_SEED and str(mark["label"]).is_empty():
		return tr_static("SENSE_EMPYRITE_SEED")
	return str(mark["label"])


## What Observation may say about an event: its kind or exact name, how far along it is, and when it moves on.
static func _event_name(mark: Dictionary) -> String:
	var label := str(mark["label"])
	var name := tr_static("SENSE_EVENT_SOMETHING")
	if tr_static("SENSE_EVENT_FAMILY_" + label) != "SENSE_EVENT_FAMILY_" + label:
		name = tr_static("SENSE_EVENT_FAMILY_" + label)
	elif not label.is_empty():
		name = tr_static("WORLD_EVENT_" + label.to_upper().replace("-", "_"))
	if int(mark["ref_id"]) > 0:
		name += ", " + tr_static("SENSE_EVENT_STAGE_%d" % int(mark["ref_id"]))
	if float(mark["depth"]) > 0.0:
		name += ", " + tr_static("SENSE_EVENT_WITHIN_HOURS") % roundi(float(mark["depth"]))
	return name


## The distance as far as a mark may show it: none for most senses' vague marks, near or far for low Observation.
static func _distance_of(mark: Dictionary, metres: float) -> String:
	if int(mark["kind"]) != Kind.EVENT:
		return "%.1fkm" % (metres / 1000.0)
	match int(float(mark["size"])):
		Precision.DIRECTION:
			return ""
		Precision.NEAR:
			return tr_static("SENSE_EVENT_NEAR")
		Precision.FAR:
			return tr_static("SENSE_EVENT_FAR")
	return "%.1fkm" % (metres / 1000.0)


static func tr_static(key: String) -> String:
	return str(TranslationServer.translate(key))
