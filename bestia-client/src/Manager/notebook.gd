extends Node

## The player's notebook as this client last heard it: routes, map marks, map preferences - one store, kept on the
## server and shared with the companion app. Each collection is a set of entries by key, each entry a
## [code]{key, revision, value}[/code] where [code]value[/code] is the parsed JSON its collection shows.
##
## [b]Changes arrive as they happen, whichever app made them.[/b] The server numbers every write to a collection,
## and an entry that arrives one past the revision held is applied as it stands; an older one is this client's own
## write coming back or a repeat, and is dropped; anything further ahead means a write was missed, and the whole
## collection is asked for again. A write of this client's is applied only when it comes back that way, so there is
## one path by which anything changes.

## [param collection] changed: listed, an entry written or deleted, or forgotten.
signal collection_changed(collection: String)

## The key of a collection that holds one document, such as preferences.
const SINGLE_KEY := "_"

## Revision held per collection; a collection with none has not been listed since [method forget].
var _revisions: Dictionary[String, int] = {}

## Entries per collection, by key.
var _entries: Dictionary[String, Dictionary] = {}


func _ready() -> void:
	ConnectionManager.notebook_received.connect(_on_listed)
	ConnectionManager.notebook_changed.connect(_on_changed)


## Asks for [param collection]; it is kept current from the answer on.
func watch(collection: String) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.notebook_list(collection)


## Drops everything held, for a change of master: its collections are another master's notebook.
func forget() -> void:
	var held := _entries.keys()
	_revisions.clear()
	_entries.clear()
	for collection in held:
		collection_changed.emit(collection)


func is_loaded(collection: String) -> bool:
	return _revisions.has(collection)


## Every entry of [param collection], oldest first.
func entries(collection: String) -> Array[Dictionary]:
	var listed: Array[Dictionary] = []
	listed.assign(_entries.get(collection, {}).values())
	return listed


## The entry under [param key], or an empty [Dictionary].
func entry(collection: String, key: String) -> Dictionary:
	return _entries.get(collection, {}).get(key, {})


## The document of a collection that holds one, or an empty [Dictionary].
func document(collection: String) -> Dictionary:
	var held := entry(collection, SINGLE_KEY)
	var value: Variant = held.get("value", {})
	return value if value is Dictionary else {}


## Keeps [param value] under [param key], or under a new key when it is empty. With [param base_revision] the
## write only lands while the entry is still at it - 0 for one that must not exist yet. Nothing changes here until
## the server tells this client it landed.
func put(collection: String, key: String, value: Variant, base_revision: int = -1) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.notebook_put(collection, key, JSON.stringify(value), base_revision)


func delete(collection: String, key: String, base_revision: int = -1) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.notebook_delete(collection, key, base_revision)


func _on_listed(message: NotebookSMSG) -> void:
	var held := {}
	for listed in message.Entries:
		var parsed := _entry_of(str(listed["key"]), int(listed["revision"]), str(listed["json"]))
		if not parsed.is_empty():
			held[parsed["key"]] = parsed

	_entries[message.Collection] = held
	_revisions[message.Collection] = message.Revision
	collection_changed.emit(message.Collection)


func _on_changed(message: NotebookChangedSMSG) -> void:
	var collection := message.Collection
	if not _revisions.has(collection):
		return

	var known: int = _revisions[collection]
	if message.Revision <= known:
		return
	if message.Revision > known + 1:
		ConnectionManager.notebook_list(collection)
		return

	var held: Dictionary = _entries.get(collection, {})
	if message.Deleted:
		held.erase(message.Key)
	else:
		var parsed := _entry_of(message.Key, message.Revision, message.Json)
		if not parsed.is_empty():
			held[message.Key] = parsed

	_entries[collection] = held
	_revisions[collection] = message.Revision
	collection_changed.emit(collection)


static func _entry_of(key: String, revision: int, json: String) -> Dictionary:
	var value: Variant = JSON.parse_string(json)
	if value == null:
		push_warning("Notebook: entry %s did not read as JSON" % key)
		return {}
	return {"key": key, "revision": revision, "value": value}
