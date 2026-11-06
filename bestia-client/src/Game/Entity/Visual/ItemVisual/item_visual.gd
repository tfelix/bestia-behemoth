extends Visual
class_name ItemVisual


var _entity_id: int = 0
var _item_id: int = 0


## The glow of an item that carries an enchantment, a seal for now.
const _ENCHANTED_TINT := Color(0.55, 0.25, 1.0)

@onready var player = $AnimationPlayer
@onready var sparkles = $Sparkles
@onready var _countdown_ring: CountdownRing = $CountdownRing

var _plain_sparkles: Material = null


func setup_visual(msg: VisualComponentSMSG) -> void:
	_entity_id = msg.EntityId
	_item_id = msg.VisualId


func _ready() -> void:
	player.play("appear")
	await player.animation_finished
	sparkles.visible = true


## Null when the item carries none. The enchantment that ends first draws the ring.
func set_item_enchantments(msg: ItemEnchantmentsComponentSMSG) -> void:
	_tint_sparkles(msg != null)
	if msg == null:
		_countdown_ring.stop()
		return

	var soonest := -1
	for i in msg.EnchantmentIds.size():
		if msg.RemainingSeconds[i] > 0.0 and (soonest < 0 or msg.RemainingSeconds[i] < msg.RemainingSeconds[soonest]):
			soonest = i
	if soonest < 0:
		_countdown_ring.stop()
	else:
		_countdown_ring.start(msg.RemainingSeconds[soonest], msg.TotalSeconds[soonest])


## The sparkle material is shared by every item, so an enchanted one gets a copy of its own.
func _tint_sparkles(enchanted: bool) -> void:
	if _plain_sparkles == null:
		_plain_sparkles = sparkles.material_override
	if not enchanted:
		sparkles.material_override = _plain_sparkles
		return

	var tinted := _plain_sparkles.duplicate() as StandardMaterial3D
	tinted.albedo_color = _ENCHANTED_TINT
	tinted.emission = _ENCHANTED_TINT
	# Bright enough to glow, not so bright that the colour washes out to white.
	tinted.emission_energy_multiplier = 1.5
	sparkles.material_override = tinted
	sparkles.amount = 12


func get_item_entity_id() -> int:
	return _entity_id


func _on_area_3d_input_event(_camera: Node, event: InputEvent, event_position: Vector3, _normal: Vector3, _shape_idx: int) -> void:
	if not MouseManager.is_click_event(event):
		return

	MouseManager.get_instance().object_clicked(self, event, event_position)


func _on_area_3d_mouse_entered() -> void:
	MouseManager.get_instance().on_object_hover(self, true)


func _on_area_3d_mouse_exited() -> void:
	MouseManager.get_instance().on_object_hover(self, false)
