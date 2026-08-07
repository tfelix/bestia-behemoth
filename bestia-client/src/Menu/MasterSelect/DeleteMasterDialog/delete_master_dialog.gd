extends ConfirmationDialog
class_name DeleteMasterDialog
## Last line of defence before a character is destroyed.
##
## Deleting a master is irreversible, so a plain yes/no is too easy to click through by accident. The
## confirm button stays disabled until the player has typed the master's name exactly, which makes the
## action deliberate rather than reflexive. The typed text is then what gets sent - the server checks it
## again rather than taking the client's word that it asked.

## Emitted once the player typed the name correctly and confirmed. [param typed_name] is deliberately the
## text the player entered, not the master's stored name, so the server can verify the prompt happened.
signal deletion_confirmed(master_id: int, typed_name: String)

@onready var _name_edit: LineEdit = %NameEdit

var _master_id: int = 0
var _master_name: String = ""


func _ready() -> void:
	_name_edit.text_changed.connect(_on_name_changed)


func open_for(master_info: MasterInfo) -> void:
	_master_id = int(master_info.MasterId)
	_master_name = master_info.Name

	dialog_text = ("Deleting %s cannot be undone. The character, its bestias, its items and everything " +
		"it has learned are lost for good.\n\nType %s below to confirm.") % [_master_name, _master_name]

	_name_edit.text = ""
	_name_edit.placeholder_text = _master_name
	_refresh_confirm_button()

	# Sized explicitly rather than left to the contents: the warning is autowrapped, so without a width to
	# wrap against the dialog stretches into one very long line.
	popup_centered(Vector2i(480, 240))
	_name_edit.grab_focus()


func _on_name_changed(_new_text: String) -> void:
	_refresh_confirm_button()


func _on_confirmed() -> void:
	# Re-checked rather than trusted to the disabled button: a dialog can also be confirmed by the
	# ui_accept action, which does not go through the button's disabled state.
	if not _is_name_confirmed():
		return

	deletion_confirmed.emit(_master_id, _name_edit.text.strip_edges())


func _is_name_confirmed() -> bool:
	return _name_edit.text.strip_edges() == _master_name


func _refresh_confirm_button() -> void:
	get_ok_button().disabled = not _is_name_confirmed()
