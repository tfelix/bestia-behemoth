extends MultiMeshInstance3D
class_name ResourceSonar

## Resource Sonar, the Miner's passive: while the master holds a pickaxe, every ore and gem block in range glows
## through the rock. The server sends nothing for it - the blocks are in the chunks this client already holds,
## see OreSonar. Placed in Game.tscn, so it lives and dies with the world.

## M_RESOURCE_SONAR in the server's skills.yml.
const _SKILL_ID := 43
## The pickaxe in the server's items.yml, by item name key, like Angler's rods.
const _PICKAXE_NAME_KEY := &"PICKAXE"
const _METRES_PER_LEVEL := 5
const _RESCAN_SECONDS := 0.5
const _MARKER_SIZE := 0.4
## Ore colours are muted earth tones; brightened so they read as a glow against dark rock.
const _GLOW_BOOST := 1.6

var _master_entity_id := 0
var _level := 0
var _since_scan := 0.0


func _ready() -> void:
	multimesh = _empty_multimesh()
	ConnectionManager.connect("self_received", _on_self_received)
	ConnectionManager.connect("entity_received", _on_entity_received)


func _on_self_received(msg: SelfSMSG) -> void:
	_master_entity_id = msg.MasterEntityId


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is SkillListSMSG and msg.EntityId == _master_entity_id:
		_level = 0
		for entry in msg.Skills:
			if entry.SkillId == _SKILL_ID:
				_level = maxi(_level, entry.Level)


func _process(delta: float) -> void:
	_since_scan += delta
	if _since_scan < _RESCAN_SECONDS:
		return
	_since_scan = 0.0
	_show(_scan())


## The blocks to light up, as Godot positions with the block id in w; empty when the sonar is quiet.
func _scan() -> PackedVector4Array:
	var miner := _miner()
	if miner == null or ConnectionManager.chunk_stream == null:
		return PackedVector4Array()

	return PackedVector4Array(ConnectionManager.chunk_stream.OreAround(miner.global_position, _METRES_PER_LEVEL * _level))


## The master, while the sonar can hear: the skill learned and a pickaxe in the right hand.
func _miner() -> Entity:
	if _level <= 0:
		return null
	var entities := EntityManager.get_instance()
	var master: Entity = entities.get_master_entity() if entities != null else null
	if master == null:
		return null
	var held: Variant = master.get_equipment().get(EquipmentSlot.Slot.RIGHT_HAND)
	if held == null:
		return null
	var item := ItemDB.get_instance().get_item(held["item_id"])
	if item == null or StringName(item.name_key) != _PICKAXE_NAME_KEY:
		return null

	return master


func _show(found: PackedVector4Array) -> void:
	multimesh.instance_count = found.size()
	for i in found.size():
		var block := found[i]
		multimesh.set_instance_transform(i, Transform3D(Basis.IDENTITY, Vector3(block.x, block.y, block.z)))
		var colour: Color = ConnectionManager.chunk_stream.BlockColour(int(block.w))
		multimesh.set_instance_color(i, Color(colour.r * _GLOW_BOOST, colour.g * _GLOW_BOOST, colour.b * _GLOW_BOOST, 0.85))


func _empty_multimesh() -> MultiMesh:
	var cube := BoxMesh.new()
	cube.size = Vector3.ONE * _MARKER_SIZE
	var glow := StandardMaterial3D.new()
	glow.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	glow.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	glow.vertex_color_use_as_albedo = true
	# Through the rock: the whole point of the sonar is to see what the terrain hides.
	glow.no_depth_test = true
	cube.material = glow

	var mesh := MultiMesh.new()
	mesh.transform_format = MultiMesh.TRANSFORM_3D
	mesh.use_colors = true
	mesh.mesh = cube
	return mesh
