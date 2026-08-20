extends VBoxContainer
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

var _shortcut_containers: Array[ShortcutContainer] = []

## The whole save file: master id (as string, JSON object keys always are) -> the slot array of
## that master. Kept in memory so saving the selected master never has to re-read the file and
## can't drop the entries of the masters that aren't currently loaded.
var _all_shortcuts: Dictionary = {}


func _ready() -> void:
	_collect_shortcut_containers()
	_connect_signals()
	load_shortcuts()


func _collect_shortcut_containers() -> void:
	_shortcut_containers.clear()
	_find_shortcut_containers()


func _find_shortcut_containers() -> void:
	var rows = get_children()
	for row in rows:
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


func _connect_inventory_signal() -> void:
	if inventory and not inventory.inventory_updated.is_connected(_on_inventory_updated):
		inventory.inventory_updated.connect(_on_inventory_updated)
		print_debug("Shortcuts: Inventory update signal connected")


func _on_shortcut_changed(_row: int, _number: int, _data: ShortcutData) -> void:
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

	var shortcuts_data = []

	for container in _shortcut_containers:
		var data = container.get_shortcut_data()
		shortcuts_data.append({
			"row": container.shortcut_row,
			"number": container.shortcut_number,
			"data": data.to_dict()
		})

	_all_shortcuts[master_key] = shortcuts_data

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
	_apply_shortcuts_for_current_master()


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


func _apply_shortcuts_for_current_master() -> void:
	# Reset via set_shortcut_data() rather than clear_shortcut(): the latter emits shortcut_changed,
	# which saves - so it would overwrite this master's stored bar before it has been read back.
	for container in _shortcut_containers:
		container.set_shortcut_data(ShortcutData.new())

	var master_key = ConnectionManager.current_master_key()
	if master_key.is_empty() or not _all_shortcuts.has(master_key):
		return

	var shortcuts_data = _all_shortcuts[master_key]
	if typeof(shortcuts_data) != TYPE_ARRAY:
		push_error("Invalid shortcuts data format for master %s" % [master_key])
		return

	for shortcut_config in shortcuts_data:
		var row = shortcut_config["row"]
		var number = shortcut_config["number"]
		var data_dict = shortcut_config["data"]

		var container = _find_container(row, number)
		if container:
			var shortcut_data = ShortcutData.from_dict(data_dict)
			container.set_shortcut_data(shortcut_data)


func clear_all_shortcuts() -> void:
	for container in _shortcut_containers:
		container.clear_shortcut()
	save_shortcuts()
