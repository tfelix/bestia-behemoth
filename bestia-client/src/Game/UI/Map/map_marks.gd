class_name MapMarks
extends Node

## The marks a player has put on their own map, kept on this machine.
##
## [b]Client-side, unlike everything else the map knows.[/b] A chart is an item the server owns because it is
## a thing that can be carried, traded and lost; a mark is a note about a place, and nobody else can see it or
## take it. So there is no table, no message and no handler - which also means a mark survives nothing but
## this installation, and that is the trade.
##
## [b]Keyed to the map, not just to the master.[/b] A mark is a world coordinate, so a regenerated world makes
## every one of them a note about terrain that is not there any more. The file therefore lives under the
## server's [code]worldMapVersion[/code] - the same key [MapSource] hangs its tile cache on, and for the same
## reason - and a new version reads an empty set rather than misplaced marks. Directories for other versions
## are swept, so a reseeded dev world does not accumulate one per reseed.
##
## Marks are per master under that: two characters on one account keep separate notes, which is what "my map"
## means when the charts are per master too.

## The set changed: added, renamed, removed, or reloaded for a different master or world.
signal marks_changed()

const _ROOT := "user://mapmarks/"

## Cap on how many one master may keep in one world.
##
## Not a storage limit - the file is bytes per mark - but a legibility one: the compass and the map both draw
## every mark in range, and a player who could drop a thousand would be able to make their own map unusable.
const MAX_MARKS := 200

const MAX_NAME_LENGTH := 32

var _marks: Array[Dictionary] = []

var _version: String = ""
var _master_id: int = 0


## Points this at a world and a character, and reads what was stored for the pair.
##
## Safe to call repeatedly with the same arguments - it reloads, which is what makes it usable as the "the
## master changed" hook without a second entry point.
func use(version: String, master_id: int) -> void:
	_version = version
	_master_id = master_id
	_marks.clear()

	if _version.is_empty() or _master_id == 0:
		marks_changed.emit()
		return

	DirAccess.make_dir_recursive_absolute(_dir())
	_discard_other_versions()
	_load()
	marks_changed.emit()


## Every mark, as [code]{name, x, y}[/code] with the position in world metres.
##
## The same shape [MapView] gets from [method MapSource.places_of], minus the fields only a world place has,
## so one drawing path can take both.
func all() -> Array[Dictionary]:
	return _marks


func count() -> int:
	return _marks.size()


## Adds a mark, returning whether there was room for it.
##
## An empty or whitespace-only name is refused rather than stored blank: a mark with no name is a dot the
## player cannot tell from the next one, and the popup that calls this can say so.
func add(mark_name: String, at: Vector2) -> bool:
	var trimmed := mark_name.strip_edges()
	if trimmed.is_empty() or _marks.size() >= MAX_MARKS:
		return false

	_marks.append({
		"name": trimmed.substr(0, MAX_NAME_LENGTH),
		"x": at.x,
		"y": at.y,
	})
	_save()
	marks_changed.emit()
	return true


func remove(index: int) -> bool:
	if index < 0 or index >= _marks.size():
		return false

	_marks.remove_at(index)
	_save()
	marks_changed.emit()
	return true


## Index of the mark nearest [param at] within [param radius] metres, or -1.
func nearest(at: Vector2, radius: float) -> int:
	var best := -1
	var best_distance := radius

	for i in _marks.size():
		var mark := _marks[i]
		var distance := Vector2(float(mark["x"]), float(mark["y"])).distance_to(at)
		if distance < best_distance:
			best_distance = distance
			best = i

	return best


func _dir() -> String:
	return _ROOT + _version + "/"


func _path() -> String:
	return "%s%d.json" % [_dir(), _master_id]


## Drops the marks of every other world.
##
## The same sweep [MapSource] performs on its tile directories, for a stronger reason: a stale tile is only
## wasted disk, and a stale mark would be a note pointing at ground that has been regenerated into something
## else. Keeping them could only ever mislead.
func _discard_other_versions() -> void:
	var root := DirAccess.open(_ROOT)
	if root == null:
		return

	for entry in root.get_directories():
		if entry == _version:
			continue

		var stale := DirAccess.open(_ROOT + entry + "/")
		if stale == null:
			continue

		for file in stale.get_files():
			stale.remove(file)
		DirAccess.remove_absolute(_ROOT + entry)


func _load() -> void:
	var file := FileAccess.open(_path(), FileAccess.READ)
	if file == null:
		return

	var json := JSON.new()
	if json.parse(file.get_as_text()) != OK or typeof(json.data) != TYPE_ARRAY:
		push_warning("MapMarks: %s was not readable; starting with none" % _path())
		return

	# Rebuilt field by field rather than trusted wholesale. This file is on disk where anything can edit it,
	# and a malformed entry that reached the drawing code would be a crash per frame rather than one bad mark.
	for entry in json.data:
		if typeof(entry) != TYPE_DICTIONARY:
			continue
		var mark_name := str(entry.get("name", "")).strip_edges()
		if mark_name.is_empty():
			continue
		_marks.append({
			"name": mark_name.substr(0, MAX_NAME_LENGTH),
			"x": float(entry.get("x", 0.0)),
			"y": float(entry.get("y", 0.0)),
		})
		if _marks.size() >= MAX_MARKS:
			break


func _save() -> void:
	if _version.is_empty() or _master_id == 0:
		return

	DirAccess.make_dir_recursive_absolute(_dir())
	var file := FileAccess.open(_path(), FileAccess.WRITE)
	if file == null:
		push_warning("MapMarks: could not write %s; this session's marks are not being kept" % _path())
		return

	file.store_string(JSON.stringify(_marks))
