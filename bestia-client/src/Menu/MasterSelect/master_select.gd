extends Control


@onready var _master_list = %MasterList
@onready var _loading_label = %LoadingLabel

func _ready() -> void:
	_clear_master_list()
	_loading_label.show()
	ConnectionManager.connect("master_info_received", _on_master_received)
	ConnectionManager.list_bestia_master()


func _on_new_master_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/CreateNewMaster/CreateNewMaster.tscn")


func _clear_master_list() -> void:
	for child in _master_list.get_children():
		child.queue_free()


func _on_master_received(master: MasterSMSG) -> void:
	_loading_label.hide()
	for master_info in master.Masters:
		var master_info_scene = MasterInfoScn.create(master_info)
		_master_list.add_child(master_info_scene)
		
