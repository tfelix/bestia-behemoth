extends Visual
class_name ItemVisual


var _entity_id: int = 0
var _item_id: int = 0


## The glow of an item that carries an enchantment, a seal for now.
const _ENCHANTED_TINT := Color(0.55, 0.25, 1.0)
## Gold, so a drop held for this player stands out from the white sparkles every item has.
const _CLAIM_GLITTER_TINT := Color(1.0, 0.78, 0.25)
## How see-through a drop is while only other players may pick it up.
const _CLAIMED_BY_OTHERS_TRANSPARENCY := 0.5

@onready var player = $AnimationPlayer
@onready var sparkles = $Sparkles
@onready var _countdown_ring: CountdownRing = $CountdownRing

var _plain_sparkles: Material = null
var _claim_glitter: GPUParticles3D = null
var _claimed_by_others := false


func setup_visual(msg: VisualComponentSMSG) -> void:
	_entity_id = msg.EntityId
	_item_id = msg.VisualId


func _ready() -> void:
	player.play("appear")
	await player.animation_finished
	sparkles.visible = not _claimed_by_others


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


## Null when anybody may pick the item up. A holder sees it glitter; everyone else sees it faded.
func set_loot_claim(msg: LootClaimComponentSMSG) -> void:
	var held_for_me := msg != null and msg.HolderAccountIds.has(ConnectionManager.account_id)
	_show_claim_glitter(held_for_me)
	_fade(msg != null and not held_for_me)


func _show_claim_glitter(shown: bool) -> void:
	if _claim_glitter == null:
		if not shown:
			return
		_claim_glitter = _make_claim_glitter()
		add_child(_claim_glitter)
	_claim_glitter.visible = shown
	_claim_glitter.emitting = shown


## A denser, golden copy of the sparkles, so it rises from the same spot as they do.
func _make_claim_glitter() -> GPUParticles3D:
	var glitter := sparkles.duplicate() as GPUParticles3D
	var gold := (_plain_sparkles if _plain_sparkles != null else sparkles.material_override).duplicate() as StandardMaterial3D
	gold.albedo_color = _CLAIM_GLITTER_TINT
	gold.emission = _CLAIM_GLITTER_TINT
	gold.emission_energy_multiplier = 2.5
	glitter.material_override = gold
	# Smaller and more of them than the sparkles, so it reads as glitter rather than as more sparkles.
	var flake := QuadMesh.new()
	flake.size = Vector2(0.08, 0.08)
	glitter.draw_pass_1 = flake
	glitter.amount = 24
	glitter.lifetime = 1.0
	return glitter


func _fade(faded: bool) -> void:
	_claimed_by_others = faded
	sparkles.visible = not faded
	for node: GeometryInstance3D in find_children("*", "GeometryInstance3D", true, false):
		if node is GPUParticles3D or _countdown_ring.is_ancestor_of(node):
			continue
		node.transparency = _CLAIMED_BY_OTHERS_TRANSPARENCY if faded else 0.0


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
