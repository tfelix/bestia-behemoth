extends TextureRect

@onready var _master_visual: MasterVisual = %MasterVisual


func apply_visual(msg: MasterVisualComponentSMSG) -> void:
	_master_visual.setup_visual(msg)
