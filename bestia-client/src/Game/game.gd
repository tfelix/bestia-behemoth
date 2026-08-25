extends Node3D

## Owns the terrain and prop renderers, and keeps the terrain's collision anchor on the player.
##
## Created here in code rather than placed in Game.tscn, for the same reason ConnectionManager creates
## ChunkStreamManager that way: a scene node needs a resource uid that only the Godot editor can mint.
## They have to live under this node specifically — they add 3D children, so they need a parent in the
## game's own 3D world rather than on the ConnectionManager autoload.

const TerrainRendererScript = preload("res://Game/World/TerrainRenderer.cs")
const FarTerrainRendererScript = preload("res://Game/World/FarTerrainRenderer.cs")
const StaticEntityRendererScript = preload("res://Game/World/StaticEntityRenderer.cs")

var _terrain: Node3D = null
var _far_terrain: Node3D = null
var _props: Node3D = null

func _ready() -> void:
	_terrain = TerrainRendererScript.new()
	_terrain.name = "Terrain"
	add_child(_terrain)

	# The coarse ring, drawn past where the chunks reach. A sibling rather than a child so the two can be
	# hidden, rebuilt or profiled independently — they share nothing but the terrain material.
	_far_terrain = FarTerrainRendererScript.new()
	_far_terrain.name = "FarTerrain"
	add_child(_far_terrain)

	# A sibling of the terrain rather than a child of it: the terrain rebuilds a chunk's mesh whenever a
	# patch arrives, and nothing standing on that ground should be torn down when it does.
	_props = StaticEntityRendererScript.new()
	_props.name = "Props"
	add_child(_props)

	if ConnectionManager.chunk_stream != null:
		ConnectionManager.chunk_stream.Renderer = _terrain
		# After the terrain, which owns the material the far ring borrows.
		ConnectionManager.chunk_stream.FarTerrain = _far_terrain
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
		ConnectionManager.chunk_stream.FarTerrain = null
		ConnectionManager.chunk_stream.StaticEntities = null


func _process(_delta: float) -> void:
	# Collision follows the player rather than covering the whole streamed disc, so only the chunks
	# they can actually click on or bump into carry a shape. Cheap to call every frame: the renderer
	# compares chunk coordinates and does nothing unless one changed.
	var player: Entity = $EntityManager.get_owned_entity()
	if player != null:
		_terrain.SetCollisionAnchorAt(player.global_position)
		# The far ring stops drawing where the real chunks start, and that hole follows the player. Cheap
		# every frame for the same reason: it is two floats the mesh builder reads when a patch lands.
		_far_terrain.SetAnchor(player.global_position)
