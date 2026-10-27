class_name MapMarks
extends Node

## The marks a master has put on their own map, and how they like the map drawn - the hidden service kinds and the
## grid. Kept in the player's [code]Notebook[/code] on the server, so the companion app shows and edits the same
## ones, and a change made there arrives here as it happens.
##
## Writes are sent, not applied: a mark appears when the server says it was kept, the same way one added in the
## companion app does.

## The set changed: added, renamed, removed - here or in the companion app - or loaded for another master.
signal marks_changed()

## The hidden service kinds changed, here, in the companion app, or loaded for another master.
signal hidden_services_changed(kinds: Array[String])

## Whether the grid is drawn changed.
signal grid_overlay_changed(on: bool)

const MARKS := "marks"
const PREFS := "map-prefs"

## As the server's `MarksCollection` holds them.
const MAX_MARKS := 200
const MAX_NAME_LENGTH := 32
const ICONS: Array[String] = ["pin", "star", "danger", "treasure", "camp", "resource"]

var _marks: Array[Dictionary] = []
var _hidden_services: Array[String] = []
var _grid_overlay := false


func _ready() -> void:
	Notebook.collection_changed.connect(_on_collection_changed)


## Asks for the marks and preferences of the master now played.
func watch() -> void:
	Notebook.watch(MARKS)
	Notebook.watch(PREFS)


## The service kinds switched off in the legend. Empty - everything shown - for a master who never touched it.
func hidden_services() -> Array[String]:
	return _hidden_services


func set_hidden_services(kinds: Array[String]) -> void:
	if kinds == _hidden_services:
		return
	_write_prefs(kinds, _grid_overlay)


func grid_overlay() -> bool:
	return _grid_overlay


func set_grid_overlay(on: bool) -> void:
	if on != _grid_overlay:
		_write_prefs(_hidden_services, on)


## Every mark, as [code]{key, revision, name, x, y, icon, note}[/code] with the position in world metres.
##
## The same [code]x[/code]/[code]y[/code] shape [MapView] gets from [method MapSource.places_of], so one drawing
## path can take both.
func all() -> Array[Dictionary]:
	return _marks


func count() -> int:
	return _marks.size()


## Sends a new mark, returning whether there was room for one and a name to give it.
##
## An empty or whitespace-only name is refused rather than stored blank: a mark with no name is a dot the player
## cannot tell from the next one, and the popup that calls this can say so.
func add(mark_name: String, at: Vector2, icon: String = "pin") -> bool:
	var trimmed := mark_name.strip_edges()
	if trimmed.is_empty() or _marks.size() >= MAX_MARKS:
		return false

	Notebook.put(MARKS, "", _body(trimmed, at, icon, ""), 0)
	return true


func rename(index: int, mark_name: String) -> bool:
	var trimmed := mark_name.strip_edges()
	if index < 0 or index >= _marks.size() or trimmed.is_empty():
		return false

	var mark := _marks[index]
	var at := Vector2(float(mark["x"]), float(mark["y"]))
	Notebook.put(MARKS, mark["key"], _body(trimmed, at, mark["icon"], mark["note"]), mark["revision"])
	return true


func remove(index: int) -> bool:
	if index < 0 or index >= _marks.size():
		return false

	Notebook.delete(MARKS, _marks[index]["key"], _marks[index]["revision"])
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


static func _body(mark_name: String, at: Vector2, icon: String, note: String) -> Dictionary:
	var body := {"name": mark_name.substr(0, MAX_NAME_LENGTH), "east": at.x, "north": at.y, "icon": icon}
	if not note.is_empty():
		body["note"] = note
	return body


func _write_prefs(hidden: Array[String], grid: bool) -> void:
	Notebook.put(PREFS, Notebook.SINGLE_KEY, {"hiddenServiceKinds": hidden, "gridOverlay": grid})


func _on_collection_changed(collection: String) -> void:
	if collection == MARKS:
		_read_marks()
		marks_changed.emit()
	elif collection == PREFS:
		_read_prefs()


## Rebuilt field by field: an entry the companion app wrote reaches the drawing code through here, and a malformed
## one must cost one mark rather than a crash per frame.
func _read_marks() -> void:
	_marks = []
	for held in Notebook.entries(MARKS):
		var value: Variant = held["value"]
		if not value is Dictionary:
			continue
		var mark_name := str(value.get("name", "")).strip_edges()
		if mark_name.is_empty():
			continue
		var note: Variant = value.get("note")
		_marks.append({
			"key": held["key"],
			"revision": held["revision"],
			"name": mark_name,
			"x": float(value.get("east", 0.0)),
			"y": float(value.get("north", 0.0)),
			"icon": str(value.get("icon", "pin")),
			"note": "" if note == null else str(note),
		})


## Shown and gridless are the defaults to fall back to: a legend that lost its settings costs a click, one that
## came back hiding everything would look like a map with no services on it.
func _read_prefs() -> void:
	var prefs := Notebook.document(PREFS)

	var hidden: Array[String] = []
	var kinds: Variant = prefs.get("hiddenServiceKinds", [])
	if kinds is Array:
		for kind in kinds:
			if kind is String and kind not in hidden:
				hidden.append(kind)
	if hidden != _hidden_services:
		_hidden_services = hidden
		hidden_services_changed.emit(_hidden_services)

	var grid := bool(prefs.get("gridOverlay", false))
	if grid != _grid_overlay:
		_grid_overlay = grid
		grid_overlay_changed.emit(_grid_overlay)
