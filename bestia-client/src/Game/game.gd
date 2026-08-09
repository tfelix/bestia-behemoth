extends Node3D

## Owns the terrain and prop renderers, and keeps the terrain's collision anchor on the player.
##
## Created here in code rather than placed in Game.tscn, for the same reason ConnectionManager creates
## ChunkStreamManager that way: a scene node needs a resource uid that only the Godot editor can mint.
## They have to live under this node specifically — they add 3D children, so they need a parent in the
## game's own 3D world rather than on the ConnectionManager autoload.

const TerrainRendererScript = preload("res://Game/World/TerrainRenderer.cs")
const StaticEntityRendererScript = preload("res://Game/World/StaticEntityRenderer.cs")
const DayNightCycleScript = preload("res://Game/World/DayNightCycle.cs")

var _terrain: Node3D = null
var _props: Node3D = null

## Drives the sun, moon, sky and fog from the world clock. Created here rather than placed in the scene
## for the same reason the renderers are, but note that it does NOT own the lights: the scene authors
## them and this hands them over, so shadow settings and angular sizes stay where a designer would look.
var _day_night: Node = null


func _ready() -> void:
	_terrain = TerrainRendererScript.new()
	_terrain.name = "Terrain"
	add_child(_terrain)

	# A sibling of the terrain rather than a child of it: the terrain rebuilds a chunk's mesh whenever a
	# patch arrives, and nothing standing on that ground should be torn down when it does.
	_props = StaticEntityRendererScript.new()
	_props.name = "Props"
	add_child(_props)

	_day_night = DayNightCycleScript.new()
	_day_night.name = "DayNight"
	add_child(_day_night)
	# Nulls are tolerated and warned about once — this is a visual layer, and a client that cannot find its
	# sky should still be playable under whatever light the scene was authored with.
	_day_night.Configure($WorldEnvironment, $Sun, $Moon, ConnectionManager.world_clock)

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


func _process(_delta: float) -> void:
	# Collision follows the player rather than covering the whole streamed disc, so only the chunks
	# they can actually click on or bump into carry a shape. Cheap to call every frame: the renderer
	# compares chunk coordinates and does nothing unless one changed.
	var player: Entity = $EntityManager.get_owned_entity()
	if player != null:
		_terrain.SetCollisionAnchorAt(player.global_position)
