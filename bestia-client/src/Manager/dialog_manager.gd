extends Node
## Queues dialogs - server-pushed and client-only alike - and hands them to whatever UI is currently able to
## show one.
##
## An autoload rather than a node in the game UI, because a [DialogSMSG] can arrive before that UI
## exists at all - the server sends dialogs as soon as it has something to say and does not wait for
## the client to finish loading. Buffering therefore has to happen above the scene: this node is
## alive for the whole session, collects dialogs from [signal ConnectionManager.dialog_received],
## and only drains the queue while a presenter is registered.
##
## Dialogs are shown strictly one at a time. Two arriving back to back would otherwise clobber each
## other, since the presenter is a single modal window.
##
## [b]Client-only dialogs go through exactly the same queue.[/b] Static information that needs nothing from
## the server - what a skill unlocks, how a screen works - has no business being a wire message, so
## [method show_local] pushes a [DialogContent] built from a key in the same [code]dialogs.csv[/code] the
## server-pushed ones are translated out of. [method show_local_once] is the same thing remembered per master,
## for a primer that should be read once and never again.
##
## Deliberately independent of [EntityManager]: a dialog is account-scoped, and its optional
## source entity is only ever presentation metadata.

## Emitted after a dialog is closed and the queue has drained. Handy for anything that wants to
## resume once the player is no longer reading (e.g. re-enabling input).
signal all_dialogs_closed()

## Which one-shot local dialogs each master has already been shown.
##
## Per master rather than per account: a primer explaining the skill tree is worth reading again on a second
## master, because it is the *master* that is new to it. Stored beside the other per-master client state - see
## [code]shortcuts.gd[/code], which keys its bar the same way.
const SEEN_DIALOGS_SAVE_PATH = "user://seen_dialogs.json"

var _queue: Array[DialogContent] = []
var _presenter: Node = null
var _showing: bool = false

## Master key -> array of local dialog keys already shown. Read once, written on every new one.
var _seen: Dictionary = {}
var _seen_loaded: bool = false


func _ready() -> void:
	ConnectionManager.dialog_received.connect(_on_dialog_received)


## Called by the dialog window as it enters the tree. Registering immediately drains whatever piled
## up while no UI was around.
func register_presenter(presenter: Node) -> void:
	_presenter = presenter
	_show_next()


func unregister_presenter(presenter: Node) -> void:
	if _presenter != presenter:
		return

	_presenter = null
	# Whatever was on screen died with the scene, so the next presenter starts fresh.
	_showing = false


## Called by the presenter once the player has confirmed or dismissed the current dialog.
func on_closed() -> void:
	_showing = false
	_show_next()

	if not _showing:
		all_dialogs_closed.emit()


## Number of dialogs waiting. Only really useful for tests and debugging.
func pending_count() -> int:
	return _queue.size()


## Shows a client-only dialog, named by a key rather than by a server catalogue id.
##
## The key resolves to [code]DIALOG_<key>_TEXT[/code] and optionally [code]DIALOG_<key>_TITLE[/code] in
## [code]dialogs.csv[/code], so local and server dialogs live in one file and translate the same way.
func show_local(key: String) -> void:
	_queue.push_back(DialogContent.of_local(key))
	_show_next()


## Shows a client-only dialog the first time this master ever sees it, and never again.
##
## Records it as seen [i]before[/i] showing rather than after being read: a player who closes the client mid-read
## has still been shown it, and the alternative - recording on dismissal - would re-open it every launch until
## somebody clicked the button, which is worse than missing it once.
##
## Silently does nothing when no master is selected, which is the case when [code]Game.tscn[/code] was run
## straight from the editor: there is nowhere to record it, so showing it would mean showing it every run.
func show_local_once(key: String) -> void:
	var master_key := _current_master_key()
	if master_key.is_empty():
		return

	_load_seen()

	var seen_for_master: Array = _seen.get(master_key, [])
	if key in seen_for_master:
		return

	seen_for_master.append(key)
	_seen[master_key] = seen_for_master
	_save_seen()

	show_local(key)


## Forgets that this master has seen [param key], so it will be shown again. For debugging and for a future
## "replay the tutorial" option; nothing calls it yet.
func forget_local(key: String) -> void:
	var master_key := _current_master_key()
	if master_key.is_empty():
		return

	_load_seen()

	var seen_for_master: Array = _seen.get(master_key, [])
	if key not in seen_for_master:
		return

	seen_for_master.erase(key)
	_seen[master_key] = seen_for_master
	_save_seen()


## Identifies whose dialogs these are. MasterId is set by ConnectionManager.select_bestia_master() before it
## switches to Game.tscn, so it is available as early as a scene's _ready - the same reasoning
## [code]shortcuts.gd[/code] spells out for its own key.
func _current_master_key() -> String:
	if ConnectionManager.selected_master_info == null:
		return ""
	return str(ConnectionManager.selected_master_info.MasterId)


func _load_seen() -> void:
	if _seen_loaded:
		return
	_seen_loaded = true

	if not FileAccess.file_exists(SEEN_DIALOGS_SAVE_PATH):
		return

	var file := FileAccess.open(SEEN_DIALOGS_SAVE_PATH, FileAccess.READ)
	if file == null:
		push_warning("DialogManager: could not read %s" % SEEN_DIALOGS_SAVE_PATH)
		return

	var parsed = JSON.parse_string(file.get_as_text())
	file.close()

	# A corrupt or hand-edited file means at worst a primer shown twice, so it is discarded quietly rather
	# than failing the launch over it.
	if parsed is Dictionary:
		_seen = parsed


func _save_seen() -> void:
	var file := FileAccess.open(SEEN_DIALOGS_SAVE_PATH, FileAccess.WRITE)
	if file == null:
		push_warning("DialogManager: could not write %s" % SEEN_DIALOGS_SAVE_PATH)
		return

	file.store_string(JSON.stringify(_seen, "\t"))
	file.close()


func _on_dialog_received(message) -> void:
	_queue.push_back(DialogContent.of_message(message))
	_show_next()


func _show_next() -> void:
	if _showing or _presenter == null or _queue.is_empty():
		return

	_showing = true
	_presenter.show_dialog(_queue.pop_front())
