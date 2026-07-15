extends Control
class_name MasterInfoScn

@onready var pos_x = %PosX
@onready var pos_y = %PosY
@onready var master_name = %MasterName
@onready var level_label = %Level
@onready var profile_image = %MasterProfileImage
@onready var _highlight = %Highlight

var _master_info: MasterInfo

static func create(master_info: MasterInfo) -> Control:
	var master_info_scn := preload("res://Menu/MasterSelect/MasterInfo/MasterInfo.tscn").instantiate()
	master_info_scn._master_info = master_info

	return master_info_scn


func _ready() -> void:
	master_name.text = _master_info.Name
	level_label.text = "Lv. %s" % _master_info.Level
	# Position is a Godot Vector3 (server z-up mapped to y-up), so the horizontal
	# map tile coordinates are x and z.
	pos_x.text = "X: %s" % str(int(_master_info.Position.x))
	pos_y.text = "Y: %s" % str(int(_master_info.Position.z))
	profile_image.load_master(_master_info)

	_highlight.hide()
	mouse_entered.connect(_on_mouse_entered)
	mouse_exited.connect(_on_mouse_exited)


func _on_mouse_entered() -> void:
	_highlight.show()


func _on_mouse_exited() -> void:
	_highlight.hide()


### Select the master and load the game world when the slot is clicked.
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		print("Selected master: ", _master_info.MasterId)
		ConnectionManager.select_bestia_master(_master_info)
