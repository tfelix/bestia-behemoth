class_name MarkDeletePopup
extends PopupPanel

## Asks whether to remove one of the player's own marks, next to the mark.
##
## The counterpart to [MarkNamePopup] and deliberately its twin: right-clicking bare ground asks for a name
## and puts a mark there, right-clicking the mark asks whether to take it away. One gesture, and which
## question it opens depends only on what is under it.
##
## Confirmed rather than immediate, unlike most map interactions, because this is the one that destroys
## something. A mark is a note the player wrote and there is no undo for it - and right-click is exactly the
## gesture they were using a moment ago to *create* one, so a mis-aimed second click would otherwise silently
## delete what the first click made.
##
## Like the name popup it touches [MapMarks] not at all: it asks, and the caller decides what the answer
## means.

## The player chose to remove the mark. Not emitted when they backed out.
signal confirmed()

@onready var _prompt: Label = $Rows/Prompt
@onready var _keep: Button = $Rows/Buttons/Keep
@onready var _delete: Button = $Rows/Buttons/Delete


func _ready() -> void:
	_keep.text = tr("MARK_DELETE_KEEP")
	_delete.text = tr("MARK_DELETE_CONFIRM")

	_keep.pressed.connect(hide)
	_delete.pressed.connect(_confirm)

	# Focus lands on Keep, not Delete: the safe answer is the one a stray Enter should give.
	about_to_popup.connect(func() -> void: _keep.grab_focus())


## Opens near [param at] in screen coordinates, naming [param mark_name] so the player can see which mark
## they hit - two marks close together are exactly when this question matters.
func ask(at: Vector2i, mark_name: String) -> void:
	_prompt.text = tr("MARK_DELETE_PROMPT") % mark_name

	popup(Rect2i(at + Vector2i(12, 12), size))


func _confirm() -> void:
	hide()
	confirmed.emit()
