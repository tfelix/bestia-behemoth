extends Node3D

## Owns the terrain renderer and keeps its collision anchor on the player.
##
## Created here in code rather than placed in Game.tscn, for the same reason ConnectionManager creates
## ChunkStreamManager that way: a scene node needs a resource uid that only the Godot editor can mint.
## It has to live under this node specifically — it adds MeshInstance3D children, so it needs a parent
## in the game's own 3D world rather than on the ConnectionManager autoload.

const TerrainRendererScript = preload("res://Game/World/TerrainRenderer.cs")

var _terrain: Node3D = null


func _ready() -> void:
	_terrain = TerrainRendererScript.new()
	_terrain.name = "Terrain"
	add_child(_terrain)

	if ConnectionManager.chunk_stream != null:
		ConnectionManager.chunk_stream.Renderer = _terrain
	else:
		push_warning("No ChunkStreamManager to render for; terrain will stay empty.")


func _process(_delta: float) -> void:
	# Collision follows the player rather than covering the whole streamed disc, so only the chunks
	# they can actually click on or bump into carry a shape. Cheap to call every frame: the renderer
	# compares chunk coordinates and does nothing unless one changed.
	var player: Entity = $EntityManager.get_owned_entity()
	if player != null:
		_terrain.SetCollisionAnchorAt(player.global_position)
