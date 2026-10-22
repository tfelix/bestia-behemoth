extends HBoxContainer
class_name Shortcuts

const SHORTCUTS_SAVE_PATH = "user://shortcuts_config.json"

## Assigned by Game/UI/ui.gd after WidgetWindow instantiates the Inventory content, which
## happens later than this node's own _ready() - so the signal connection can't happen
## inline here and is deferred to the setter instead.
@export var inventory: Inventory = null:
	set(value):
		inventory = value
		if is_node_ready():
			_connect_inventory_signal()

@onready var _rows: VBoxContainer = %Rows
@onready var _pager: VBoxContainer = %PagerButtons

var _shortcut_containers: Array[ShortcutContainer] = []
var _layer_label: Label

## The whole save file: master id (as string, JSON object keys always are) -> that master's
## [method ShortcutLayout.to_dict]. Kept in memory so saving the selected master never has to re-read
## the file and can't drop the entries of the masters that aren't currently loaded.
var _all_shortcuts: Dictionary = {}
var _layout := ShortcutLayout.new()
## Whose layers the bar shows: the master's, or those of the bestia the player controls.
var _entity_key := ShortcutLayout.MASTER_KEY


func _ready() -> void:
	add_to_group("world_blocking_ui")
	_build_pager()
	_collect_shortcut_containers()
	_connect_signals()
	load_shortcuts()


func _shortcut_input(event: InputEvent) -> void:
	for layer in ShortcutLayout.LAYER_COUNT:
		if event.is_action_pressed(_layer_action(layer), false, true):
			select_layer(layer)
			get_viewport().set_input_as_handled()
			return


func select_layer(layer: int) -> void:
	_layout.set_active_layer(_entity_key, layer)
	_show_active_layer()
	save_shortcuts()


func _cycle_layer(step: int) -> void:
	select_layer(posmod(_active_layer() + step, ShortcutLayout.LAYER_COUNT))


func _active_layer() -> int:
	return _layout.active_layer_of(_entity_key)


func _build_pager() -> void:
	var up := _pager_button("▲", "Next bar", 1)
	_layer_label = UiStyle.label("", 16, UiStyle.TEXT)
	_layer_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_layer_label.mouse_filter = Control.MOUSE_FILTER_PASS
	var down := _pager_button("▼", "Previous bar", -1)
	for control in [up, _layer_label, down]:
		_pager.add_child(control)


func _pager_button(glyph: String, tooltip: String, step: int) -> Button:
	var button := UiStyle.glyph_button(glyph, tooltip)
	button.add_theme_color_override("font_hover_color", UiStyle.TEXT)
	button.pressed.connect(_cycle_layer.bind(step))
	return button


func _update_pager() -> void:
	_layer_label.text = str(_active_layer() + 1)
	var first := _hotkey_of(_layer_action(0))
	var last := _hotkey_of(_layer_action(ShortcutLayout.LAYER_COUNT - 1))
	_layer_label.tooltip_text = "Bar %d of %d (%s to %s)\nYour master and every bestia have their own bars." % [
		_active_layer() + 1, ShortcutLayout.LAYER_COUNT, first, last]


static func _layer_action(layer: int) -> StringName:
	return StringName("shortcut_layer_%d" % [layer + 1])


static func _hotkey_of(action: StringName) -> String:
	for event in InputMap.action_get_events(action):
		if event is InputEventKey:
			return event.as_text_physical_keycode()
	return ""


func _collect_shortcut_containers() -> void:
	_shortcut_containers.clear()
	_find_shortcut_containers()


func _find_shortcut_containers() -> void:
	for row in _rows.get_children():
		for child in row.get_children():
			if child.get_script() and child.get_script().get_global_name() == "ShortcutContainer":
				_shortcut_containers.append(child)


func _connect_signals() -> void:
	for container in _shortcut_containers:
		if not container.shortcut_changed.is_connected(_on_shortcut_changed):
			container.shortcut_changed.connect(_on_shortcut_changed)
		if not container.item_count_requested.is_connected(_on_item_count_requested):
			container.item_count_requested.connect(_on_item_count_requested)
	_connect_inventory_signal()

	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)


func _on_controlled_entity_changed(_previous_id: int, entity_id: int) -> void:
	_entity_key = ShortcutLayout.entity_key_of(entity_id, EntityManager.get_instance().get_owned_master_entity_id())
	_show_active_layer()


func _connect_inventory_signal() -> void:
	if inventory and not inventory.inventory_updated.is_connected(_on_inventory_updated):
		inventory.inventory_updated.connect(_on_inventory_updated)
		print_debug("Shortcuts: Inventory update signal connected")


func _on_shortcut_changed(row: int, number: int, data: ShortcutData) -> void:
	_layout.set_slot(_entity_key, _active_layer(), row, number, data)
	save_shortcuts()


func _on_item_count_requested(_row: int, _number: int, _item_id: int) -> void:
	var container = _find_container(_row, _number)
	if container:
		var item_count = _get_item_count_from_inventory(_item_id)
		container.update_item_count(item_count)


func _on_inventory_updated() -> void:
	# Update all item counts when inventory changes
	for container in _shortcut_containers:
		var data = container.get_shortcut_data()
		if data.type == ShortcutData.ShortcutType.ITEM:
			var count = _get_item_count_from_inventory(data.reference_id)
			container.update_item_count(count)


func _get_item_count_from_inventory(item_id: int) -> int:
	if not inventory:
		print_debug("Shortcuts: Item count updated too early, inventory not set yet")
		return 0
	if inventory.is_initialized_for_current_entity() == false:
		print_debug("Shortcuts: Item count updated too early, inventory not initialized yet")
		pass
	return inventory.get_item_count(item_id)


func _find_container(row: int, number: int) -> ShortcutContainer:
	for container in _shortcut_containers:
		if container.shortcut_row == row and container.shortcut_number == number:
			return container
	return null


func save_shortcuts() -> void:
	var master_key = ConnectionManager.current_master_key()
	if master_key.is_empty():
		push_warning("Shortcuts: no master selected, not persisting the bar")
		return

	_all_shortcuts[master_key] = _layout.to_dict()

	var json_string = JSON.stringify(_all_shortcuts, "\t")
	var file = FileAccess.open(SHORTCUTS_SAVE_PATH, FileAccess.WRITE)
	if file:
		file.store_string(json_string)
		file.close()
		print("Shortcuts of master %s saved to: %s" % [master_key, SHORTCUTS_SAVE_PATH])
	else:
		push_error("Failed to save shortcuts configuration")


func load_shortcuts() -> void:
	_read_store_from_disk()
	_layout = ShortcutLayout.from_dict(_all_shortcuts.get(ConnectionManager.current_master_key()))
	_show_active_layer()


func _read_store_from_disk() -> void:
	_all_shortcuts = {}

	if not FileAccess.file_exists(SHORTCUTS_SAVE_PATH):
		print("No shortcuts configuration found, starting fresh")
		return

	var file = FileAccess.open(SHORTCUTS_SAVE_PATH, FileAccess.READ)
	if not file:
		push_error("Failed to load shortcuts configuration")
		return

	var json_string = file.get_as_text()
	file.close()

	var json = JSON.new()
	var error = json.parse(json_string)

	if error != OK:
		push_error("Failed to parse shortcuts JSON: ", json.get_error_message())
		return

	if typeof(json.data) != TYPE_DICTIONARY:
		# Pre-per-master files were a bare array shared by every master. There is no way to tell
		# which master built it, so it is dropped rather than handed to an arbitrary one.
		push_warning("Discarding shortcuts configuration in the old, master-agnostic format")
		return

	_all_shortcuts = json.data
	print("Shortcuts loaded from: %s" % [SHORTCUTS_SAVE_PATH])


func _show_active_layer() -> void:
	# Through set_shortcut_data() rather than set_shortcut(): the latter emits shortcut_changed, which would
	# write every slot shown straight back into the layout.
	var layer := _active_layer()
	for container in _shortcut_containers:
		container.set_shortcut_data(_layout.get_slot(_entity_key, layer, container.shortcut_row, container.shortcut_number))
	_update_pager()


func clear_all_shortcuts() -> void:
	for container in _shortcut_containers:
		container.clear_shortcut()
	save_shortcuts()
