class_name ItemDB

static var _instance: ItemDB = null
var _items := {}
const _item_db_dir = "res://Game/Item/DB/"

# Get or create the singleton instance
static func get_instance() -> ItemDB:
	if _instance == null:
		_instance = ItemDB.new()
		_instance._load_items()
	return _instance

# Initialize the singleton (call this once at game startup)
static func initialize() -> ItemDB:
	if _instance == null:
		_instance = ItemDB.new()
		_instance._load_items()
	return _instance

# Clear the singleton instance (useful for testing or cleanup)
static func clear_instance() -> void:
	_instance = null

func _init():
	# Private constructor - use get_instance() or initialize() instead
	pass


func _load_items() -> void:
	var dir := DirAccess.open(_item_db_dir)
	var loaded_items_count = 0
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var item = load(_item_db_dir + file_name)
				if item and "item_id" in item:
					_items[item.item_id] = item
					loaded_items_count += 1
			file_name = dir.get_next()
		dir.list_dir_end()
	print("ItemDB: Loaded %s items" % [loaded_items_count])

func get_item(item_id: int) -> ItemResource:
	return _items.get(item_id, null)
