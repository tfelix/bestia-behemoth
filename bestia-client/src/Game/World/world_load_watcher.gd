class_name WorldLoadWatcher extends Node
## Decides when the world around the player is still arriving, and has SceneManager say so.
##
## The counterpart to [LoadingScreen], which draws and knows nothing. Everything here is about the
## chunk stream and the player's own entity, and none of it draws.
##
## Entities need no condition of their own: the server announces one when the chunk it stands in
## reaches this client, behind that chunk's payload on the same ordered stream, so terrain that is
## held implies the entities standing on it have arrived.

## Farther than this between two authoritative positions is a jump rather than a walk. A walking
## entity is resynced every 8 tiles against a 32-tile chunk, so two chunks cannot pass between syncs.
const _JUMP_CHUNKS := 2.0

## How long the load may make no progress at all before the screen comes down anyway.
##
## This replaces a flat 30 s cap on the whole load, which could not tell a slow load from a stuck one and
## regularly picked the wrong answer. Every streaming budget on the server is per tick, so a tick running long
## - a login into dense ground, where props are being materialised and columns costed - stretches the whole
## delivery without anything being wrong with it. The old cap then fired mid-load and revealed a world that
## was still arriving, which reads exactly like terrain that failed to load.
##
## Stalling is the thing actually worth giving up on, and it is cheap to detect: either the delivered fraction
## rises or the build backlog falls, and while neither moves for this long nothing is coming.
const _STALL_SECONDS := 10.0

## The hard cap, for a load that inches forward for ever rather than stopping outright.
const _FAILSAFE_SECONDS := 180.0

const _GAME_SCENE := "res://Game/Game.tscn"
const _STREAMING := "Streaming terrain..."
const _BUILDING := "Building world..."

var _connection: Node = null
var _owned_entity_id: int = 0
var _last_server_position := Vector3.ZERO
var _has_server_position: bool = false
var _elapsed: float = 0.0
var _stalled_for: float = 0.0

## The best the stream has ever reported this load. Compared against rather than the last reading, so a
## measure that dips - a manifest withdrawing columns as the view moves - does not read as progress when it
## recovers.
var _best_progress: float = 0.0
var _least_backlog: int = 0


func _ready() -> void:
	set_process(false)


## Wires up the message sources.
##
## Called by ConnectionManager rather than done in [method _ready], for the reason it calls
## chunk_stream.Attach: a child coming up inside an autoload's own _ready() cannot rely on that
## autoload's global name being bound yet.
func attach(connection: Node) -> void:
	_connection = connection
	connection.self_received.connect(_on_self_received)
	connection.entity_received.connect(_on_entity_received)
	SceneManager.scene_changed.connect(_on_scene_changed)


## The ground under the player is about to change wholesale. Raises the screen and starts watching.
func begin() -> void:
	_elapsed = 0.0
	_stalled_for = 0.0
	_best_progress = 0.0
	# Nothing has been measured yet, so the first reading has to count as progress whatever it is.
	_least_backlog = 1 << 30
	SceneManager.show_loading(_STREAMING)
	set_process(true)


## Stops watching without revealing anything, for a connection that has gone.
func abort() -> void:
	set_process(false)
	SceneManager.hide_loading(false)


func _process(delta: float) -> void:
	_elapsed += delta

	var stream: Node = _connection.chunk_stream if _connection != null else null
	if stream == null:
		abort()
		return

	var progress: float = stream.ViewLoadProgress
	SceneManager.set_loading_progress(progress, _BUILDING if progress >= 1.0 else _STREAMING)

	if _is_world_ready(stream):
		_finish()
		return

	_age_stall(stream, progress, delta)

	if _stalled_for < _STALL_SECONDS and _elapsed < _FAILSAFE_SECONDS:
		return

	push_warning("World load gave up after %.0f s (%.0f s of it stalled at %.0f%%): %s; %s"
		% [_elapsed, _stalled_for, progress * 100.0, stream.Summary(), _renderer_summary(stream)])
	_finish()


## Advances or resets the stall timer. Either measure moving means the load is still going somewhere.
func _age_stall(stream: Node, progress: float, delta: float) -> void:
	var backlog: int = stream.BuildBacklog

	if progress > _best_progress or backlog < _least_backlog:
		_best_progress = maxf(_best_progress, progress)
		_least_backlog = mini(_least_backlog, backlog)
		_stalled_for = 0.0
		return

	_stalled_for += delta


func _finish() -> void:
	set_process(false)
	SceneManager.hide_loading()


func _is_world_ready(stream: Node) -> bool:
	if not stream.ViewLoadComplete:
		return false

	var entities := EntityManager.get_instance()
	if entities == null:
		return false

	var player: Entity = entities.get_owned_entity()
	if player == null:
		return false

	# Held is not the same as drawn: the surface under the player is what they are about to stand on,
	# and it reads NaN until their own chunk has been meshed.
	var ground: float = stream.GroundYAt(
		player.global_position.x, player.global_position.z, player.global_position.y)

	return not is_nan(ground)


## A different body means a different view. Today only the first self message moves this; a
## master-to-bestia switch will too.
func _on_self_received(msg: SelfSMSG) -> void:
	if msg.MasterEntityId == _owned_entity_id:
		return

	_owned_entity_id = msg.MasterEntityId
	_has_server_position = false
	begin()


func _on_entity_received(msg: EntitySMSG) -> void:
	if _owned_entity_id == 0 or msg.EntityId != _owned_entity_id or not (msg is PositionComponent):
		return

	var position: Vector3 = msg.Position
	var jumped: bool = _has_server_position \
		and position.distance_to(_last_server_position) > _jump_distance()

	_last_server_position = position
	_has_server_position = true

	if jumped:
		begin()


## INF until the world info has arrived, so nothing is taken for a jump before there is a world.
func _jump_distance() -> float:
	var stream: Node = _connection.chunk_stream if _connection != null else null
	if stream == null or stream.WorldInfo == null:
		return INF

	return _JUMP_CHUNKS * float(stream.ChunkExtentMetres)


## Only a load this watcher started. The login handshake raises the same screen and is not ours to
## take down.
func _on_scene_changed(path: String) -> void:
	if path != _GAME_SCENE and is_processing():
		abort()


func _renderer_summary(stream: Node) -> String:
	return stream.Renderer.Summary() if stream.Renderer != null else "no renderer"
