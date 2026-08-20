extends Control
class_name MasterInfoScn

## Emitted when the player asks to delete this master. Only a request: the slot does not act on it, the
## selection screen owns the confirmation prompt and the message to the server.
signal delete_requested(master_info: MasterInfo)

@onready var pos_x = %PosX
@onready var pos_y = %PosY
@onready var master_name = %MasterName
@onready var level_label = %Level
@onready var profile_image = %MasterProfileImage
@onready var _highlight = %Highlight
@onready var _delete_button: Button = %DeleteButton

var _master_info: MasterInfo

## Returns the concrete type rather than a plain Control so callers can reach [signal delete_requested]
## without the static analyser rejecting it.
static func create(master_info: MasterInfo) -> MasterInfoScn:
	var master_info_scn := preload("res://Menu/MasterSelect/MasterInfo/MasterInfo.tscn").instantiate() as MasterInfoScn
	master_info_scn._master_info = master_info

	return master_info_scn


func _ready() -> void:
	master_name.text = _master_info.Name
	level_label.text = "Lv. %s" % _master_info.Level
	# Named with the server's axes, the same way the in-game profile window is. Only the horizontal
	# pair is shown here, so there is no height to label - but the swap still goes through the one
	# helper, so the two screens cannot drift apart again.
	var shown := TileSpace.to_server_axes(_master_info.Position)
	pos_x.text = "X: %s" % str(int(shown.x))
	pos_y.text = "Y: %s" % str(int(shown.y))
	profile_image.load_master(_master_info)

	_highlight.hide()
	mouse_entered.connect(_on_mouse_entered)
	mouse_exited.connect(_on_mouse_exited)
	_delete_button.pressed.connect(_on_delete_pressed)


func _on_delete_pressed() -> void:
	delete_requested.emit(_master_info)


func _on_mouse_entered() -> void:
	_highlight.show()


func _on_mouse_exited() -> void:
	_highlight.hide()


### Select the master and load the game world when the slot is clicked.
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		print("Selected master: ", _master_info.MasterId)
		ConnectionManager.select_bestia_master(_master_info)
