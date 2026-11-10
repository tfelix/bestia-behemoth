extends Area3D
class_name Bobber

## A master's float on the water, with the line back to their hand. A click on the player's own bobber
## strikes. An Area3D for the reason PropPicker gives: the floor ray passes through it, a click does not.

const _LINE_FROM_HEIGHT := 1.4
const _BOB_HEIGHT := 0.04
const _BOB_SPEED := 2.5
const _PLUNGE_DEPTH := 0.18

## The angler's entity id.
var angler_id: int = 0

var is_biting := false

@onready var _float: Node3D = $Float
@onready var _line: MeshInstance3D = $Line

var _time := 0.0


func _ready() -> void:
	_line.mesh = ImmediateMesh.new()


func show_line(msg: FishingComponentSMSG) -> void:
	angler_id = int(msg.EntityId)
	is_biting = msg.Biting
	global_position = TileSpace.tile_centre(msg.Bobber)


func is_own() -> bool:
	var master := EntityManager.get_instance().get_master_entity()
	return master != null and master.entity_id == angler_id


func _process(delta: float) -> void:
	_time += delta
	var bob := sin(_time * _BOB_SPEED) * _BOB_HEIGHT
	_float.position.y = bob - (_PLUNGE_DEPTH if is_biting else 0.0)
	_draw_line()


func _draw_line() -> void:
	var mesh := _line.mesh as ImmediateMesh
	mesh.clear_surfaces()

	var angler := EntityManager.get_instance().get_entity(angler_id)
	if angler == null:
		return

	mesh.surface_begin(Mesh.PRIMITIVE_LINES)
	mesh.surface_add_vertex(to_local(angler.global_position + Vector3.UP * _LINE_FROM_HEIGHT))
	mesh.surface_add_vertex(_float.position)
	mesh.surface_end()


func _on_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_mouse_entered() -> void:
	MouseManager.get_instance().on_object_hover(self, true)


func _on_mouse_exited() -> void:
	MouseManager.get_instance().on_object_hover(self, false)
