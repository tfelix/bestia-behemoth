extends Control


@onready var master_list = %MasterList


func _ready() -> void:
	ConnectionManager.connect("master_info_received", _on_master_received)
	ConnectionManager.list_bestia_master()


func _on_new_master_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/CreateNewMaster/CreateNewMaster.tscn")


func _on_master_received(master: MasterSMSG) -> void:
	for master_info in master.Masters:
		var master_info_scene = MasterInfoScn.create(master_info)
		master_list.add_child(master_info_scene)
		#master_info_scene.master_info = master_info
		
