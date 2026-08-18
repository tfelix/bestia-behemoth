class_name EffectDB

## Loads every [EffectResource] under DB/ once and keys them by effect_id - same singleton +
## directory-scan shape as [AttackDB], [ItemDB] and [BestiaDB].

static var _instance: EffectDB = null
var _effects := {}
const _effect_db_dir = "res://Game/Entity/Visual/EffectVisual/DB/"


static func get_instance() -> EffectDB:
	if _instance == null:
		_instance = EffectDB.new()
		_instance._load_effects()
	return _instance


static func initialize() -> EffectDB:
	return get_instance()


static func clear_instance() -> void:
	_instance = null


func _load_effects() -> void:
	var dir := DirAccess.open(_effect_db_dir)
	var loaded_count = 0
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var effect = load(_effect_db_dir + file_name)
				if effect and "effect_id" in effect:
					_effects[effect.effect_id] = effect
					loaded_count += 1
			file_name = dir.get_next()
		dir.list_dir_end()
	print("EffectDB: Loaded %s effects" % [loaded_count])


func get_effect(effect_id: int) -> EffectResource:
	return _effects.get(effect_id, null)
