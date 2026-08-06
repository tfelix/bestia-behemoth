extends AcceptDialog
class_name MessageDialog
## Shows one server-pushed dialog: a block of localized text and a confirm button.
##
## Registers itself with the [code]DialogManager[/code] autoload, which owns the queue and decides
## what to show when - so this node only ever deals with the dialog currently on screen. That split
## is what lets a dialog arriving during scene loading survive until there is something to show it
## in.
##
## Only [code]CONFIRM[/code] dialogs exist so far. Player-choice dialogs will add buttons here and
## send the answer back through [code]ConnectionManager[/code]; the queue and text resolution above
## and below this node do not change for that.

const _DEFAULT_TITLE_KEY := "DIALOG_DEFAULT_TITLE"

## BBCode and sizing live in MessageDialog.tscn, so dialog text can use the same markup as skill
## descriptions (see skill_row.gd) without this script re-asserting layout.
@onready var _body: RichTextLabel = %Body

var _current = null


func _ready() -> void:
	confirmed.connect(_on_closed)
	canceled.connect(_on_closed)

	DialogManager.register_presenter(self)


func _exit_tree() -> void:
	DialogManager.unregister_presenter(self)


## Called by DialogManager. Never call this directly - going through the manager is what keeps
## dialogs from overwriting each other.
func show_dialog(message) -> void:
	_current = message

	_body.text = DialogText.resolve(message)

	var dialog_title := DialogText.resolve_title(message)
	title = dialog_title if not dialog_title.is_empty() else tr(_DEFAULT_TITLE_KEY)

	popup_centered()


## Both [signal AcceptDialog.confirmed] and [signal Window.canceled] land here, and closing a dialog
## can raise both, so this stays idempotent - a double call would otherwise pop (and silently skip)
## the next queued dialog.
func _on_closed() -> void:
	if _current == null:
		return

	_current = null
	DialogManager.on_closed()
