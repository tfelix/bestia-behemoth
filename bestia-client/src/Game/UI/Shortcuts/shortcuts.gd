extends VBoxContainer
class_name Shortcuts

const SHORTCUTS_SAVE_PATH = "user://shortcuts_config.json"

@export var inventory: Inventory = null

var _shortcut_containers: Array[ShortcutContainer] = []


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
	if inventory:
		inventory.inventory_updated.connect(_on_inventory_updated)


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
		return 0
	return inventory.get_item_count(item_id)


func _find_container(row: int, number: int) -> ShortcutContainer:
	for container in _shortcut_containers:
		if container.shortcut_row == row and container.shortcut_number == number:
			return container
	return null


func save_shortcuts() -> void:
	var shortcuts_data = []

	for container in _shortcut_containers:
		var data = container.get_shortcut_data()
		shortcuts_data.append({
			"row": container.shortcut_row,
			"number": container.shortcut_number,
			"data": data.to_dict()
		})

	var json_string = JSON.stringify(shortcuts_data, "\t")
	var file = FileAccess.open(SHORTCUTS_SAVE_PATH, FileAccess.WRITE)
	if file:
		file.store_string(json_string)
		file.close()
		print("Shortcuts saved to: ", SHORTCUTS_SAVE_PATH)
	else:
		push_error("Failed to save shortcuts configuration")


func load_shortcuts() -> void:
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

	var shortcuts_data = json.data
	if typeof(shortcuts_data) != TYPE_ARRAY:
		push_error("Invalid shortcuts data format")
		return

	for shortcut_config in shortcuts_data:
		var row = shortcut_config["row"]
		var number = shortcut_config["number"]
		var data_dict = shortcut_config["data"]

		var container = _find_container(row, number)
		if container:
			var shortcut_data = ShortcutData.from_dict(data_dict)
			container.set_shortcut_data(shortcut_data)

	print("Shortcuts loaded from: ", SHORTCUTS_SAVE_PATH)


func clear_all_shortcuts() -> void:
	for container in _shortcut_containers:
		container.clear_shortcut()
	save_shortcuts()
