extends Node
## Queues server-pushed dialogs and hands them to whatever UI is currently able to show one.
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
## Deliberately independent of [EntityManager]: a dialog is account-scoped, and its optional
## source entity is only ever presentation metadata.

## Emitted after a dialog is closed and the queue has drained. Handy for anything that wants to
## resume once the player is no longer reading (e.g. re-enabling input).
signal all_dialogs_closed()

var _queue: Array = []
var _presenter: Node = null
var _showing: bool = false


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


func _on_dialog_received(message) -> void:
	_queue.push_back(message)
	_show_next()


func _show_next() -> void:
	if _showing or _presenter == null or _queue.is_empty():
		return

	_showing = true
	_presenter.show_dialog(_queue.pop_front())
