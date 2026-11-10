class_name SensedMarks
extends RefCounted

## What Resource Sense and Echo Sounding found, held until it fades. Each mark points at a spot in map metres; the
## compass and the chat show it, and nothing is put on the map for good.

## The proto's SenseKind, in its order.
enum Kind { ORE, HERBS, TIMBER, GAME, HOLLOW }

const _COLOURS := {
	Kind.ORE: Color(0.85, 0.62, 0.35),
	Kind.HERBS: Color(0.45, 0.85, 0.45),
	Kind.TIMBER: Color(0.30, 0.62, 0.30),
	Kind.GAME: Color(0.95, 0.55, 0.45),
	Kind.HOLLOW: Color(0.65, 0.65, 0.85),
}

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
		var to := Vector2(float(mark["x"]), float(mark["y"]))
		result.append({
			"bearing": CompassStrip.bearing_between(Vector3(here.x, 0.0, here.y), Vector3(to.x, 0.0, to.y)),
			"colour": _COLOURS.get(int(mark["kind"]), Color.WHITE),
			"label": "%s %.1fkm" % [name_of(mark), here.distance_to(to) / 1000.0],
		})
	return result


## One chat line for a reading, as seen from [param here].
static func summary(message: SenseMarksSMSG, here: Vector2) -> String:
	if message.Marks.is_empty():
		return tr_static("SENSE_NOTHING")

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
	return str(mark["label"])


static func tr_static(key: String) -> String:
	return str(TranslationServer.translate(key))
