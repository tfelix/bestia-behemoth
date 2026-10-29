class_name EntityFxDB

## Loads every [EntityFxDef] under DB/ once and keys those the server can ask for by kind - the same singleton
## + directory-scan shape as [EmoteDB]. Adding an effect is one more resource here, edited in the inspector.

static var _instance: EntityFxDB = null
var _defs := {}
const _fx_db_dir = "res://Game/Entity/Fx/DB/"


static func get_instance() -> EntityFxDB:
	if _instance == null:
		_instance = EntityFxDB.new()
		_instance._load_defs()
	return _instance


func _load_defs() -> void:
	var dir := DirAccess.open(_fx_db_dir)
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var def = load(_fx_db_dir + file_name)
				if def is EntityFxDef and def.kind != "":
					_defs[def.kind] = def
			file_name = dir.get_next()
		dir.list_dir_end()
	print("EntityFxDB: Loaded %s effects" % [_defs.size()])


## The effect of the server's EntityFxId [param kind] ("WING_VANISH"), or null when the client has none.
func get_fx(kind: String) -> EntityFxDef:
	return _defs.get(kind.to_upper(), null)


## Every effect, for the harness's picker.
func all() -> Array[EntityFxDef]:
	var defs: Array[EntityFxDef] = []
	defs.assign(_defs.values())
	defs.sort_custom(func(a: EntityFxDef, b: EntityFxDef) -> bool: return a.resource_path < b.resource_path)
	return defs
