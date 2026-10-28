extends TextureRect

@onready var _viewport: SubViewport = $PortraitViewport
@onready var _master_visual: MasterVisual = $PortraitViewport/MasterProfile/MasterVisual


## Dresses the visual as the master looks in the world. The Mage's own meshes are hidden in its scene, so a
## visual nobody dressed shows nothing but the backdrop.
func load_master(master: MasterInfo) -> void:
	var look := MasterVisualComponentSMSG.new()
	look.Body = master.BodyType
	look.Face = master.Face
	look.Hair = master.Hair
	look.HairColor = master.HairColor
	look.SkinColor = master.SkinColor
	_master_visual.setup_visual(look)
	_render_once()


func _render_once() -> void:
	# A frame later, so the new parts have reached the render server. The viewport renders only once, and
	# its first frame is taken before the master is dressed.
	await get_tree().process_frame
	_viewport.render_target_update_mode = SubViewport.UPDATE_ONCE
