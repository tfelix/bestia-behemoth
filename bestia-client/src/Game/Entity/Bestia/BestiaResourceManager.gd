class_name BestiaResourceManager extends RefCounted

static var _instance: BestiaResourceManager
static var _loaded_bestia_data: Dictionary = {}
static var _bestia_registry: Dictionary = {}

static func get_instance() -> BestiaResourceManager:
	if not _instance:
		_instance = BestiaResourceManager.new()
		_load_registry()
	return _instance

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

static func get_bestia_data(bestia_id: int) -> BestiaData:
	# Return cached data if available
	if _loaded_bestia_data.has(bestia_id):
		return _loaded_bestia_data[bestia_id]
	
	# Load from registry
	if not _bestia_registry.has(str(bestia_id)):
		push_error("Bestia ID %d not found in registry" % bestia_id)
		return null
	
	var resource_path = _bestia_registry[str(bestia_id)]
	var bestia_data = load(resource_path) as BestiaData
	
	if bestia_data:
		_loaded_bestia_data[bestia_id] = bestia_data
		return bestia_data
	else:
		push_error("Failed to load bestia data from %s" % resource_path)
		return null
