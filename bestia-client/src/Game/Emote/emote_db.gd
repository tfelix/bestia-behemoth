class_name EmoteDB

## Loads every [EmoteResource] under DB/ once and keys them by kind - the same singleton + directory-scan shape as
## [EffectDB]. Adding an emote on the client is one more resource here, edited in the inspector.

static var _instance: EmoteDB = null
var _emotes := {}
const _emote_db_dir = "res://Game/Emote/DB/"


static func get_instance() -> EmoteDB:
	if _instance == null:
		_instance = EmoteDB.new()
		_instance._load_emotes()
	return _instance


static func initialize() -> EmoteDB:
	return get_instance()


static func clear_instance() -> void:
	_instance = null


func _load_emotes() -> void:
	var dir := DirAccess.open(_emote_db_dir)
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var emote = load(_emote_db_dir + file_name)
				if emote is EmoteResource:
					_emotes[emote.kind] = emote
			file_name = dir.get_next()
		dir.list_dir_end()
	print("EmoteDB: Loaded %s emotes" % [_emotes.size()])


## The emote of the server's AnimationKind [param kind] ("CHEER"), or null when that kind is no emote.
func get_emote(kind: String) -> EmoteResource:
	return _emotes.get(kind.to_upper(), null)


## Every emote, in the order the emote window lists them.
func all() -> Array[EmoteResource]:
	var emotes: Array[EmoteResource] = []
	emotes.assign(_emotes.values())
	emotes.sort_custom(func(a: EmoteResource, b: EmoteResource) -> bool: return a.resource_path < b.resource_path)
	return emotes


## The emote a typed chat command asks for ("/cheer"), or null when it is no emote.
func from_command(text: String) -> EmoteResource:
	var word := text.strip_edges()
	if not word.begins_with("/"):
		return null
	# Lowercase only, as the server matches it: /Cheer is no command there, so it is none here either.
	var command := word.substr(1)
	return get_emote(command) if command == command.to_lower() else null


## Whether the entity the player controls has clips for [param emote]. True while its body is not known yet: the
## server is asked then, and a body that cannot play it simply shows nothing.
static func controlled_can_play(emote: EmoteResource) -> bool:
	var entity_manager := EntityManager.get_instance()
	var entity := entity_manager.get_controlled_entity() if entity_manager != null else null
	var visual := entity.get_visual() if entity != null else null
	return visual == null or emote.clips_for(visual) != null
