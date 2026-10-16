extends VBoxContainer
class_name CharacterPreview

## The master being created, standing on a turntable next to the creation steps. Dragging turns it.

const TURN_PER_PIXEL := 0.01

@onready var _viewport_container: SubViewportContainer = %ViewportContainer
@onready var _turntable: Node3D = %Turntable
@onready var _visual: MasterVisual = %MasterVisual


func _ready() -> void:
	_viewport_container.gui_input.connect(_on_viewport_gui_input)


## Dresses the preview through [method MasterVisual.setup_visual], so a master looks the same here as in
## the game world. [param body], [param face] and [param hair] are proto BodyType, Face and Hairstyle values.
func show_look(body: int, face: int, hair: int, hair_color: Color, skin_color: Color) -> void:
	var look := MasterVisualComponentSMSG.new()
	look.Body = body
	look.Face = face
	look.Hair = hair
	look.HairColor = hair_color
	look.SkinColor = skin_color
	_visual.setup_visual(look)


func _on_viewport_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion and event.button_mask & MOUSE_BUTTON_MASK_LEFT:
		_turntable.rotate_y(event.relative.x * TURN_PER_PIXEL)
		_viewport_container.accept_event()
