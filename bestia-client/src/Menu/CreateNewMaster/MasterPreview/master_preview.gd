class_name MasterPreview extends TextureRect
## The master being created, turning on the spot, so the player sees each pick as they make it.

const _TURN_STEP_DEGREES := 45.0

@onready var _master_visual: MasterVisual = %MasterVisual


func show_look(body: int, face: int, hair: int, hair_color: Color, skin_color: Color) -> void:
	var look := MasterVisualComponentSMSG.new()
	look.Body = body
	look.Face = face
	look.Hair = hair
	look.HairColor = hair_color
	look.SkinColor = skin_color
	_master_visual.setup_visual(look)


func turn_left() -> void:
	_master_visual.rotation_degrees.y -= _TURN_STEP_DEGREES


func turn_right() -> void:
	_master_visual.rotation_degrees.y += _TURN_STEP_DEGREES
