extends TextureRect

@onready var _master_visual: MasterVisual = %MasterVisual
@onready var _viewport: Viewport = %PortraitViewport

func apply_visual(msg: MasterVisualComponentSMSG) -> void:
	_master_visual.setup_visual(msg)
	# Render it once again after the model has been updated.
	await get_tree().process_frame
	_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
