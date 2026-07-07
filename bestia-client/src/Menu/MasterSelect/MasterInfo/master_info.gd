extends Control
class_name MasterInfoScn

@onready var pos_x = %PosX
@onready var pos_y = %PosY
@onready var master_name = %MasterName
@onready var profile_image = %MasterProfileImage

var _master_info: MasterInfo

static func create(master_info: MasterInfo) -> Control:
	var master_info_scn := preload("res://Menu/MasterSelect/MasterInfo/MasterInfo.tscn").instantiate()
	master_info_scn._master_info = master_info
	
	return master_info_scn


func _ready() -> void:
	master_name.text = _master_info.Name
	pos_x.text = str(_master_info.Position.x)
	pos_y.text = str(_master_info.Position.y)
	profile_image.load_master(_master_info)


### Select the master and loads the game world
func _on_select_button_pressed() -> void:
	print("Selected master: ", _master_info.MasterId)
	ConnectionManager.select_bestia_master(_master_info)
