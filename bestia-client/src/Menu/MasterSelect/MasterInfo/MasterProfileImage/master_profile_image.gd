extends TextureRect

@onready var master_profile_node = %MasterProfile

func load_master(master: MasterInfo) -> void:
	# must load the master scene and put the data in so the scene can
	# properly load the master and show him.
	# master_profile_node.add_child(master_scene)
	pass
