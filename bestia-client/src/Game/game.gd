extends Node3D

## Owns the terrain and prop renderers, and keeps the terrain's collision anchor on the player.
##
## Created here in code rather than placed in Game.tscn, for the same reason ConnectionManager creates
## ChunkStreamManager that way: a scene node needs a resource uid that only the Godot editor can mint.
## They have to live under this node specifically — they add 3D children, so they need a parent in the
## game's own 3D world rather than on the ConnectionManager autoload.

const TerrainRendererScript = preload("res://Game/World/TerrainRenderer.cs")
const StaticEntityRendererScript = preload("res://Game/World/StaticEntityRenderer.cs")
const TerrainGrassScript = preload("res://Game/World/TerrainGrass.cs")
const MouseManagerScript = preload("res://Game/Mouse/mouse_manager.gd")
const MovementPilotScript = preload("res://Game/Movement/movement_pilot.gd")

var _terrain: Node3D = null
var _props: Node3D = null
var _grass: Node3D = null
var _mouse: MouseManager = null
var _pilot: MovementPilot = null

func _ready() -> void:
	# Everything the mouse layer holds is world state, so it lives and dies with this scene rather than
	# with the process. Added first: it joins its group in _enter_tree(), so it is findable the moment
	# add_child returns, and every caller resolves it through MouseManager.get_instance().
	_mouse = MouseManagerScript.new()
	_mouse.name = "MouseManager"
	add_child(_mouse)

	# Steers the player towards a map pin or a prop. Scene-scoped for the same reason: a walk in
	# progress means nothing once the world it was crossing is gone.
	_pilot = MovementPilotScript.new()
	_pilot.name = "MovementPilot"
	add_child(_pilot)

	_terrain = TerrainRendererScript.new()
	_terrain.name = "Terrain"
	add_child(_terrain)

	# A sibling of the terrain rather than a child of it: the terrain rebuilds a chunk's mesh whenever a
	# patch arrives, and nothing standing on that ground should be torn down when it does.
	_props = StaticEntityRendererScript.new()
	_props.name = "Props"
	add_child(_props)

	# The decorative grass field, which is the terrain's and not the props'. It is a sample of the ground
	# rather than something standing on it, so it has to be rebuilt whenever the ground is - which is why the
	# terrain drives it directly instead of the chunk stream doing so. A sibling all the same, so that the
	# terrain re-meshing a chunk frees one node and not a MultiMesh with thousands of transforms in it.
	_grass = TerrainGrassScript.new()
	_grass.name = "Grass"
	add_child(_grass)
	_terrain.Grass = _grass

	if ConnectionManager.chunk_stream != null:
		ConnectionManager.chunk_stream.Renderer = _terrain
		# Assigned after the terrain, so the ground of a replayed chunk is queued before the props standing
		# on it. Both setters replay what has already arrived — see ChunkStreamManager.
		ConnectionManager.chunk_stream.StaticEntities = _props
	else:
		push_warning("No ChunkStreamManager to render for; terrain will stay empty.")


func _exit_tree() -> void:
	# The renderers die with this scene, but ConnectionManager is an autoload and outlives it. Handing
	# back a freed node would leave the manager calling into a disposed object on the next login.
	if ConnectionManager.chunk_stream != null:
		ConnectionManager.chunk_stream.Renderer = null
		ConnectionManager.chunk_stream.StaticEntities = null

		# The connection can outlive this scene - returning to master selection does exactly that - and the
		# zone drops the subscription the moment the account has no active entity. Terrain kept past that
		# receives no more patches.
		ConnectionManager.chunk_stream.ResetView()

	# The terrain and the grass die together with this scene, so this is only about not leaving a freed node
	# reachable in between.
	if _terrain != null:
		_terrain.Grass = null


func _process(_delta: float) -> void:
	# Collision follows the player rather than covering the whole streamed disc, so only the chunks
	# they can actually click on or bump into carry a shape. Cheap to call every frame: the renderer
	# compares chunk coordinates and does nothing unless one changed.
	var player: Entity = $EntityManager.get_owned_entity()
	if player != null:
		_terrain.SetCollisionAnchorAt(player.global_position)

		# The grass measures its level of detail from the player rather than from the camera, so that zooming
		# out stops thinning the field the player is standing in. TerrainGrass works the zoom out for itself
		# from how far this is from the camera — see SetFocusAt.
		if _grass != null:
			_grass.SetFocusAt(player.global_position)
