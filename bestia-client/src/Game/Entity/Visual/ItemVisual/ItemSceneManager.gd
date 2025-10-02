class_name ItemSceneManager extends RefCounted

static var _instance: ItemSceneManager
static var _loaded_scene_data: Dictionary = {}
static var _bestia_registry: Dictionary = {}


static func get_instance() -> ItemSceneManager:
	if not _instance:
		_instance = ItemSceneManager.new()
		_load_registry()
	return _instance


static func get_item_scene(item_id: int) -> PackedScene:
	# Return cached data if available
	if _loaded_scene_data.has(item_id):
		return _loaded_scene_data[item_id]
	
	# Load from registry
	if not _bestia_registry.has(str(item_id)):
		push_error("Item ID %d not found in registry" % item_id)
		return null
	
	var resource_path = _bestia_registry[str(item_id)]
	var scene_data = load(resource_path) as PackedScene
	
	if scene_data:
		_loaded_scene_data[item_id] = scene_data
		return scene_data
	else:
		push_error("Failed to load bestia data from %s" % resource_path)
		return null


static func _load_registry() -> void:
	# Load the registry file that maps bestia_id to resource path
	var registry_path = "res://data/bestia/bestia_registry.json"
	if FileAccess.file_exists(registry_path):
		var file = FileAccess.open(registry_path, FileAccess.READ)
		var json_string = file.get_as_text()
		file.close()
		
		var json = JSON.new()
		var parse_result = json.parse(json_string)
		if parse_result == OK:
			_bestia_registry = json.data
