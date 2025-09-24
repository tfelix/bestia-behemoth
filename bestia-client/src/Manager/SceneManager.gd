extends Node

var current_scene = null


var loading_screen: LoadingScreen
var _loading_screen_scene: PackedScene = preload("res://Menu/LoadingScreen/LoadingScreen.tscn")

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

	# Create and start new loading screen
	loading_screen = _loading_screen_scene.instantiate() as LoadingScreen
	get_tree().root.add_child(loading_screen)
	loading_screen.start_transition()
	await loading_screen.anim_player.animation_finished

	# Check if this transition was cancelled while waiting for animation
	if current_transition_id != _transition_id:
		return

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

	# Clean up current loading screen if it exists
	if loading_screen != null:
		loading_screen.queue_free()
		loading_screen = null

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

	if loading_screen != null:
		loading_screen.finish_transition()
		# wait for LoadingScreen's transition to finish playing
		await loading_screen.anim_player.animation_finished
		loading_screen.queue_free()
		loading_screen = null


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
