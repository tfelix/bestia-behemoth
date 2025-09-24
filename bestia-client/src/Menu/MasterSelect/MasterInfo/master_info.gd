extends Control

var MasterProfileImage = preload("res://Menu/MasterSelect/MasterInfo/MasterInfo.tscn")


var master_info: MasterInfo


@onready var pos_x = %PosX
@onready var pos_y = %PosY
@onready var master_name = %MasterName
@onready var profile_image = $HBoxContainer/MasterProfileImage


func _ready() -> void:
	master_name.text = master_info.Name
	pos_x.text = str(master_info.Position.x)
	pos_y.text = str(master_info.Position.y)
	profile_image.load_master(master_info)


### Select the master and loads the game world
func _on_select_button_pressed() -> void:
	print("Selected master: ", master_info.MasterId)
	ConnectionManager.select_bestia_master(master_info)
