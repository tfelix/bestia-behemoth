class_name BestiaDB

## Loads every [BestiaResource] under DB/ once and keys them by bestia_id - same singleton +
## directory-scan shape as [ItemDB] and AttackDB.

static var _instance: BestiaDB = null
var _bestias := {}
const _bestia_db_dir = "res://Game/Bestia/DB/"


static func get_instance() -> BestiaDB:
	if _instance == null:
		_instance = BestiaDB.new()
		_instance._load_bestias()
	return _instance


static func initialize() -> BestiaDB:
	return get_instance()


static func clear_instance() -> void:
	_instance = null


func _init():
	# Private constructor - use get_instance() or initialize() instead
	pass


func _load_bestias() -> void:
	var dir := DirAccess.open(_bestia_db_dir)
	var loaded_count = 0
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var bestia = load(_bestia_db_dir + file_name)
				if bestia and "bestia_id" in bestia:
					_bestias[bestia.bestia_id] = bestia
					loaded_count += 1
			file_name = dir.get_next()
		dir.list_dir_end()
	print("BestiaDB: Loaded %s bestias" % [loaded_count])


func get_bestia(bestia_id: int) -> BestiaResource:
	return _bestias.get(bestia_id, null)


## The equipment slot mask of a species, or 0 when the species is unknown to this client build.
func get_equip_slots(bestia_id: int) -> int:
	var bestia = get_bestia(bestia_id)
	if bestia == null:
		printerr("BestiaDB: Bestia with ID %s not found" % [bestia_id])
		return 0
	return bestia.equip_slots
