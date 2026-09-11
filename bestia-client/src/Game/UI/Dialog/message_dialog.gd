extends AcceptDialog
class_name MessageDialog
## Shows one queued dialog: a block of localized text and a confirm button.
##
## Server-pushed and client-only dialogs are indistinguishable from here, which is the point of
## [DialogContent] - both arrive as a pair of translation keys plus any placeholder values.
##
## Registers itself with the [code]DialogManager[/code] autoload, which owns the queue and decides
## what to show when - so this node only ever deals with the dialog currently on screen. That split
## is what lets a dialog arriving during scene loading survive until there is something to show it
## in.
##
## Both one-shot dialogs and conversations come through here. A conversation is a dialog whose
## [member DialogContent.options] is non-empty: the options become buttons and the OK button goes away,
## because a conversation is answered by choosing rather than by dismissing. The queue and the text
## resolution do not change for that, which is why this grew rather than a second scene being added -
## [code]DialogManager[/code] holds a single presenter slot, and two presenters would mean two copies of
## the [signal confirmed]/[signal canceled]/[member _current] dance.

const _DEFAULT_TITLE_KEY := "DIALOG_DEFAULT_TITLE"

## BBCode and sizing live in MessageDialog.tscn, so dialog text can use the same markup as skill
## descriptions (see skill_row.gd) without this script re-asserting layout.
@onready var _body: RichTextLabel = %Body

## Buttons for a conversation's options, rebuilt per dialog. Empty for everything else.
@onready var _choices: VBoxContainer = %Choices

var _current = null


func _ready() -> void:
	confirmed.connect(_on_closed)
	canceled.connect(_on_closed)

	DialogManager.register_presenter(self)


func _exit_tree() -> void:
	DialogManager.unregister_presenter(self)


## Called by DialogManager. Never call this directly - going through the manager is what keeps
## dialogs from overwriting each other.
func show_dialog(content: DialogContent) -> void:
	_current = content

	_body.text = DialogText.resolve(content)

	_build_choices(content)

	title = _title_for(content)

	popup_centered()


## Replaces the option buttons, and hides the OK button when there are any.
##
## Freed rather than reused: an option list is short and rebuilt at most once per click, and pooling
## buttons would mean remembering which are still bound to a topic nobody is offering any more.
func _build_choices(content: DialogContent) -> void:
	for child in _choices.get_children():
		child.queue_free()

	get_ok_button().visible = content.options.is_empty()

	for option in content.options:
		var button := Button.new()
		button.text = DialogText.resolve_line(option.Line.Key, Array(option.Line.Args))
		button.pressed.connect(_on_option_pressed.bind(option.TopicId))
		_choices.add_child(button)


## A speaker's name wins over a title key, and both over the default.
##
## The name is already text rather than a key - it is invented by the generator and belongs to no
## language - so it is never put through [method Object.tr].
func _title_for(content: DialogContent) -> String:
	if not content.title_override.is_empty():
		return content.title_override

	var dialog_title := DialogText.resolve_title(content)

	return dialog_title if not dialog_title.is_empty() else tr(_DEFAULT_TITLE_KEY)


## Answers the conversation and closes, in that order.
##
## Closing first would clear [member _current] and with it the callable this needs. The reply arrives as
## a fresh dialog and queues behind this one closing, which is what keeps a conversation one window deep
## rather than a stack of them.
func _on_option_pressed(topic_id: int) -> void:
	var content := _current as DialogContent
	if content == null:
		return

	if content.on_choice.is_valid():
		content.on_choice.call(topic_id)

	hide()
	_on_closed()


## Both [signal AcceptDialog.confirmed] and [signal Window.canceled] land here, and closing a dialog
## can raise both, so this stays idempotent - a double call would otherwise pop (and silently skip)
## the next queued dialog.
func _on_closed() -> void:
	if _current == null:
		return

	_current = null
	DialogManager.on_closed()
