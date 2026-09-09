class_name WorldLoadingScreen extends CanvasLayer
## Hides the game until the world around the player is actually there.
##
## Raised when the player enters the game and whenever the server puts them down somewhere else,
## lowered once the chunk stream reports its view volume delivered and drawn. Distinct from
## [LoadingScreen], which is SceneManager's scene-transition curtain: a teleport changes no scene.

## Farther than this between two authoritative positions is a jump rather than a walk. A walking
## entity is resynced every 8 tiles against a 32-tile chunk, so two chunks cannot pass between syncs.
const _JUMP_CHUNKS := 2.0

## Nothing keeps the player behind the curtain longer than this, whatever the stream reports.
const _FAILSAFE_SECONDS := 30.0

## How long the world takes to fade in once it is there. SceneManager's own curtain never shows during
## this transition - it is a plain Control on the default canvas layer, and this is a CanvasLayer above
## it - so without a fade of its own the reveal is a hard cut.
const _REVEAL_SECONDS := 0.4

const _GAME_SCENE := "res://Game/Game.tscn"

@onready var _fade: Control = %Fade
@onready var _bar: ProgressBar = %Progress
@onready var _status: Label = %Status
@onready var _spinner: TextureRect = %Spinner

var _connection: Node = null
var _owned_entity_id: int = 0
var _last_server_position := Vector3.ZERO
var _has_server_position: bool = false
var _elapsed: float = 0.0
var _reveal_tween: Tween = null


func _ready() -> void:
	hide()
	set_process(false)


## Wires up the message sources.
##
## Called by ConnectionManager rather than done in [method _ready], for the reason it calls
## chunk_stream.Attach: a child added during an autoload's own _ready() cannot rely on that
## autoload's global name being bound yet.
func attach(connection: Node) -> void:
	_connection = connection
	connection.self_received.connect(_on_self_received)
	connection.entity_received.connect(_on_entity_received)
	SceneManager.scene_changed.connect(_on_scene_changed)


## Raises the screen. Calling it while it is already up only restarts the failsafe.
func begin() -> void:
	_cancel_reveal()
	_fade.modulate.a = 1.0
	_elapsed = 0.0
	_bar.value = 0.0
	_status.text = "Streaming terrain..."
	show()
	set_process(true)


## Hides the screen at once. The ready path goes through [method _reveal] instead; this is for the
## cases where there is nothing to reveal - a dropped connection, or a scene that is not the game.
func dismiss() -> void:
	_cancel_reveal()

	if not visible:
		return

	hide()
	set_process(false)
	_fade.modulate.a = 1.0


## Fades the world in rather than cutting to it. Processing stops here: the decision is made.
func _reveal() -> void:
	if _reveal_tween != null:
		return

	set_process(false)
	_reveal_tween = create_tween()
	_reveal_tween.tween_property(_fade, "modulate:a", 0.0, _REVEAL_SECONDS)
	_reveal_tween.tween_callback(_finish_reveal)


func _finish_reveal() -> void:
	_reveal_tween = null
	dismiss()


func _cancel_reveal() -> void:
	if _reveal_tween == null:
		return

	_reveal_tween.kill()
	_reveal_tween = null


func _input(_event: InputEvent) -> void:
	# Everything, so a click behind the curtain cannot walk the player or open a window.
	if visible:
		get_viewport().set_input_as_handled()


func _process(delta: float) -> void:
	_elapsed += delta
	_spinner.rotation += delta * 3.0

	var stream: Node = _connection.chunk_stream if _connection != null else null
	if stream == null:
		dismiss()
		return

	var progress: float = stream.ViewLoadProgress
	_bar.value = progress * 100.0
	if progress >= 1.0:
		_status.text = "Building world..."

	if _is_world_ready(stream):
		_reveal()
		return

	if _elapsed >= _FAILSAFE_SECONDS:
		push_warning("WorldLoadingScreen gave up after %.0f s: %s; %s"
			% [_elapsed, stream.Summary(), _renderer_summary(stream)])
		_reveal()


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


## Unknowable before the world info arrives, and INF there means nothing is ever taken for a jump.
func _jump_distance() -> float:
	var stream: Node = _connection.chunk_stream if _connection != null else null
	var info = stream.WorldInfo if stream != null else null
	if info == null:
		return INF

	return _JUMP_CHUNKS * float(info.ChunkSize) * float(info.VoxelSizeMetres)


func _on_scene_changed(path: String) -> void:
	if path != _GAME_SCENE:
		dismiss()


func _renderer_summary(stream: Node) -> String:
	return stream.Renderer.Summary() if stream.Renderer != null else "no renderer"
