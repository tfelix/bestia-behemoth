class_name MarkNamePopup
extends PopupPanel

## Asks what to call a place, next to the place.
##
## A [PopupPanel] rather than a dialog in the middle of the screen: the player right-clicked a point on the
## map and the question is about that point, so the answer belongs beside it while it is still visible. It
## also gets Esc and click-away closing for free, which is most of what this would otherwise have to write.
##
## Deliberately does not touch [MapMarks]. It asks a question and reports the answer through
## [signal submitted]; what that means - a new mark, a rename - is the caller's to decide, which is what lets
## the same popup serve both without knowing that either exists.

## The player confirmed a name. Never emitted with a blank one - see [method _confirm].
signal submitted(mark_name: String)

@onready var _prompt: Label = $Rows/Prompt
@onready var _name: LineEdit = $Rows/Name


func _ready() -> void:
	_name.text_submitted.connect(func(_text: String) -> void: _confirm())
	# Focus is taken when the popup is shown rather than here: `about_to_popup` fires every time, whereas this
	# runs once and would leave the second question needing a click before it could be typed into.
	about_to_popup.connect(_take_focus)


## Opens the popup near [param at] in screen coordinates, empty and ready to type into.
func ask(at: Vector2i) -> void:
	_prompt.text = tr("MARK_NAME_PROMPT")
	_name.text = ""

	# Offset so the popup sits below-right of the cursor rather than under it, where its own panel would
	# cover the point the player just picked.
	popup(Rect2i(at + Vector2i(12, 12), size))


func _take_focus() -> void:
	_name.grab_focus()
	_name.select_all()


func _confirm() -> void:
	var entered := _name.text.strip_edges()
	if entered.is_empty():
		# Nothing to report and nothing to complain about: an empty box and Enter is how a player backs out
		# of a question, and Esc already means the same thing.
		hide()
		return

	hide()
	submitted.emit(entered)
