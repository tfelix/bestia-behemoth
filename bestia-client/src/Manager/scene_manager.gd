extends Node

## Emitted once a transition has swapped in its new scene, with the path it was loaded from.
signal scene_changed(path: String)

## Emitted once the loading screen is gone and the player is looking at what they waited for.
signal loading_finished()

var current_scene = null


var _scene_fade: SceneFade
var _scene_fade_scene: PackedScene = preload("res://Menu/SceneFade/SceneFade.tscn")

## Kept in its own field rather than beside _scene_fade, because the two have different lifetimes:
## every goto_scene frees the fade, while the loading screen has to survive the very scene swap it
## is covering.
var _loading: LoadingScreen = null
var _loading_scene: PackedScene = preload("res://Menu/LoadingScreen/LoadingScreen.tscn")

## Set by show_loading() while a fade is still running, so the screen can come up out of the black
## rather than cutting over a scene the player can still see.
var _loading_armed_caption: String = ""
var _is_loading_armed: bool = false

var _is_blocking: bool = false
var _is_loading: bool = false
var _content_path: String = ""
var _transition_id: int = 0  # Unique ID for each transition to handle cancellation
var _loaded_resource: Resource = null  # Store loaded resource when blocking is enabled


func goto_scene(content_path: String, is_blocking: bool = false) -> void:
	# Cancel any ongoing transition
	_cancel_current_transition()

	# Increment transition ID for this new transition
	_transition_id += 1
	var current_transition_id = _transition_id

	_is_blocking = is_blocking
	_is_loading = true
	_content_path = content_path

	# Create and start new scene fade
	_scene_fade = _scene_fade_scene.instantiate() as SceneFade
	get_tree().root.add_child(_scene_fade)
	_scene_fade.start_transition()
	await _scene_fade.anim_player.animation_finished

	# Check if this transition was cancelled while waiting for animation
	if current_transition_id != _transition_id:
		return

	# The screen belongs on top of black, not on top of the scene being left behind.
	if _is_loading_armed:
		_is_loading_armed = false
		_raise_loading(_loading_armed_caption)

	var loader = ResourceLoader.load_threaded_request(content_path)
	if not ResourceLoader.exists(content_path) or loader == null:
		# This will keep the loader in a broken state as the started transitions never finish
		printerr("Invalid content to load")
		_reset_transition_state()
		return


func _cancel_current_transition() -> void:
	# Cancel any ongoing resource loading
	if _content_path != "":
		# Note: Godot doesn't have a direct way to cancel threaded loading,
		# but we can ignore the result by clearing the path and incrementing the ID
		_content_path = ""

	# Clean up current scene fade if it exists
	if _scene_fade != null:
		_scene_fade.queue_free()
		_scene_fade = null

	# Reset state (this will also clear _loaded_resource)
	_reset_transition_state()


func _reset_transition_state() -> void:
	_is_blocking = false
	_is_loading = false
	_content_path = ""
	_loaded_resource = null


func _instantiate_and_switch_scene(resource: Resource) -> void:
	var instantiated_scene = resource.instantiate()
	var outgoing_scene = get_tree().current_scene

	# Remove the old scene
	outgoing_scene.queue_free()

	# Add and set the new scene to current
	get_tree().root.call_deferred("add_child", instantiated_scene)
	# Make sure to add it before the loading screen, so it is behind the black transition.
	get_tree().root.call_deferred("move_child", instantiated_scene, get_tree().root.get_child_count() - 2)
	get_tree().set_deferred("current_scene", instantiated_scene)

	_is_loading = false
	_loaded_resource = null

	# From the resource rather than _content_path, which the loader clears before it gets here - and
	# which the blocking path never had by the time the resource is finally instantiated.
	scene_changed.emit(resource.resource_path)

	_finalize_transition()


func unblock_transition() -> void:
	# If we have a loaded resource waiting, instantiate it now
	if _loaded_resource != null:
		_instantiate_and_switch_scene(_loaded_resource)

	_is_blocking = false
	_finalize_transition()


func _finalize_transition() -> void:
	# Check if we should wait for an external call to continue the transition.
	if _is_blocking:
		return

	# If we called unblock_transition() first but are still loading this guards against continuing
	# with an incomplete loaded scene.
	if _is_loading:
		return

	if _scene_fade != null:
		_scene_fade.finish_transition()
		# wait for the fade's outro to finish playing
		await _scene_fade.anim_player.animation_finished
		_scene_fade.queue_free()
		_scene_fade = null


## Raises the loading screen for a wait whose length is not knowable yet.
##
## Called while a transition is still fading, it waits for the black rather than cutting over the
## outgoing scene. Called with none in flight - a teleport, which changes no scene - it fades in
## over whatever is there.
func show_loading(caption: String) -> void:
	if _scene_fade != null and not _is_loading_visible():
		_loading_armed_caption = caption
		_is_loading_armed = true
		return

	_raise_loading(caption)


## A figure between 0 and 1 for a wait that can be measured. Ignored if nothing is being shown.
func set_loading_progress(value: float, caption: String) -> void:
	if _loading == null:
		return

	_loading.set_progress(value, caption)


## True while the player is being made to wait: the screen is up, or a transition has one armed to
## come up out of its black.
##
## Anything that would interrupt the player - a dialog, say - should hold off while this is true and
## come back on [signal loading_finished], since the incoming scene and its UI are built *behind*
## the screen and being in the tree is not the same as being looked at.
func is_loading_screen_up() -> bool:
	return _is_loading_armed or _is_loading_visible()


## [param fade] false when there is nothing worth revealing, e.g. a connection that just dropped.
func hide_loading(fade: bool = true) -> void:
	var was_up := is_loading_screen_up()
	_is_loading_armed = false

	if _is_loading_visible():
		# loading_finished follows from the screen's own dismissal, so a reveal still fading out is
		# not yet reported as over.
		if fade:
			_loading.reveal()
		else:
			_loading.dismiss()
		return

	# Armed but never actually raised - there is nothing to take down, yet whatever held off waiting
	# for the screen still has to be let go.
	if was_up:
		loading_finished.emit()


## Made once and reused, unlike the fade, so a reveal still running cannot be freed underneath its
## own tween. A child of this node rather than of root: root's child order is what
## _instantiate_and_switch_scene positions the incoming scene against.
func _raise_loading(caption: String) -> void:
	if _loading == null:
		_loading = _loading_scene.instantiate() as LoadingScreen
		_loading.dismissed.connect(loading_finished.emit)
		add_child(_loading)

	_loading.appear(caption)


## Showing, or part way through fading out - either way it is already on screen and needs no handover.
func _is_loading_visible() -> bool:
	return _loading != null and _loading.visible


# We observe the current loading state of the requsted file.
func _process(_delta: float) -> void:
	if _content_path == "":
		return

	var load_status = ResourceLoader.load_threaded_get_status(_content_path)

	match load_status:
		ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
			# It takes some time until the resource loader recognized our load command so we see
			# this condition here actually a few times.
			return
		ResourceLoader.THREAD_LOAD_FAILED:
			printerr("Load failed for: ", _content_path)
			_reset_transition_state()
			return
		ResourceLoader.THREAD_LOAD_LOADED:
			var loaded_resource = ResourceLoader.load_threaded_get(_content_path)
			var content_path_copy = _content_path  # Store path before clearing it
			_content_path = ""  # Clear path immediately to prevent duplicate processing

			if loaded_resource != null:
				if _is_blocking:
					# Store the resource and wait for unblock_transition() to be called
					_loaded_resource = loaded_resource
					_is_loading = false
				else:
					# Immediately instantiate the scene for non-blocking transitions
					_instantiate_and_switch_scene(loaded_resource)
			else:
				printerr("Failed to instantiate loaded resource for: ", content_path_copy)
				_reset_transition_state()
			return
