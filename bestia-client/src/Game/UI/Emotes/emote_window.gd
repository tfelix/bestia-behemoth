extends PanelContainer
class_name EmoteWindow

## Every emote, and the emote bar on Alt+1 to Alt+0 above them. Clicking an emote plays it, dragging one onto the bar
## binds it to that key, and dragging a slot off the bar clears it.
##
## The bar's keys are answered by [Shortcuts], which owns the layout and saves it; this window only shows and edits
## it, so a closed window still emotes.

## Assigned by Game/UI/ui.gd once WidgetWindow has made this content, like [member Shortcuts.inventory].
var shortcuts: Shortcuts = null:
	set(value):
		shortcuts = value
		if shortcuts != null and not shortcuts.emote_bar_changed.is_connected(_refresh):
			shortcuts.emote_bar_changed.connect(_refresh)
		if is_node_ready():
			_refresh()

@onready var _bar: HBoxContainer = %Bar
@onready var _tiles: GridContainer = %Tiles

var _tile_by_kind: Dictionary = {}


func _ready() -> void:
	for emote in EmoteDB.get_instance().all():
		var tile := _make_tile(emote)
		_tiles.add_child(tile)
		_tile_by_kind[emote.kind] = tile
	for slot in _slots():
		slot.shortcut_changed.connect(_on_slot_changed)
	visibility_changed.connect(_refresh)
	_refresh()


func _make_tile(emote: EmoteResource) -> Button:
	var tile := Button.new()
	tile.custom_minimum_size = Vector2(76, 52)
	tile.icon = emote.icon
	tile.text = tr(emote.label_key) if emote.icon != null else "%s\n%s" % [emote.glyph, tr(emote.label_key)]
	tile.tooltip_text = "/%s" % [emote.command()]
	tile.pressed.connect(_play.bind(emote))
	tile.set_drag_forwarding(_drag_data.bind(emote), Callable(), Callable())
	return tile


func _play(emote: EmoteResource) -> void:
	ConnectionManager.send_emote(emote.kind)


func _drag_data(_at_position: Vector2, emote: EmoteResource) -> Variant:
	var preview := UiStyle.label(emote.glyph, 18)
	set_drag_preview(preview)
	return {"type": "emote", "kind": emote.kind}


func _on_slot_changed(_row: int, number: int, data: ShortcutData) -> void:
	if shortcuts != null:
		shortcuts.set_emote_slot(number, data)


func _slots() -> Array[ShortcutContainer]:
	var slots: Array[ShortcutContainer] = []
	for child in _bar.get_children():
		if child is ShortcutContainer:
			slots.append(child)
	return slots


## Through set_shortcut_data rather than set_shortcut, which would emit shortcut_changed and write every slot shown
## straight back. Also greys out what the body the player controls cannot play.
func _refresh() -> void:
	if not is_node_ready():
		return
	for slot in _slots():
		var data := shortcuts.get_emote_slot(slot.shortcut_number) if shortcuts != null else ShortcutData.new()
		slot.set_shortcut_data(data)
	for kind in _tile_by_kind:
		var emote := EmoteDB.get_instance().get_emote(kind)
		(_tile_by_kind[kind] as Button).disabled = not EmoteDB.controlled_can_play(emote)
