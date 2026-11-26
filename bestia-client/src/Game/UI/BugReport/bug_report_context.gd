class_name BugReportContext
## What a bug report says about the client: the machine, the settings, the connection and the game state the
## player saw. Plain values only, so the issue can list them as a table.

const LOG_TAIL_LINES := 500
## How far back from the log's end to read; 500 lines of Godot output fit easily.
const LOG_TAIL_BYTES := 256 * 1024


static func collect(viewport: Viewport) -> Dictionary:
	return {
		"client": _client(viewport),
		"machine": _machine(),
		"performance": _performance(),
		"connection": _connection(),
		"game": _game(viewport),
		"settings": _settings(),
	}


## The selected master's id, or 0 before one is selected.
static func master_id() -> int:
	var info = ConnectionManager.selected_master_info
	return int(info.MasterId) if info != null else 0


## The end of this session's Godot log. Godot may not have flushed its last lines yet in a release build.
static func log_tail() -> String:
	var path := str(ProjectSettings.get_setting("debug/file_logging/log_path", "user://logs/godot.log"))
	return tail_of_file(path)


static func tail_of_file(path: String) -> String:
	var file := FileAccess.open(path, FileAccess.READ)
	if file == null:
		return ""

	var start := maxi(0, file.get_length() - LOG_TAIL_BYTES)
	file.seek(start)
	var lines := file.get_buffer(file.get_length() - start).get_string_from_utf8().split("\n")
	if start > 0:
		lines.remove_at(0)  # cut mid-line by the seek
	return "\n".join(lines.slice(maxi(0, lines.size() - LOG_TAIL_LINES)))


static func _client(viewport: Viewport) -> Dictionary:
	var scene := viewport.get_tree().current_scene
	return {
		"version": SettingsManager.VERSION,
		"godot": Engine.get_version_info().string,
		"debugBuild": OS.is_debug_build(),
		"scene": scene.scene_file_path if scene != null else "",
		"locale": TranslationServer.get_locale(),
		"uptimeSeconds": Time.get_ticks_msec() / 1000,
	}


static func _machine() -> Dictionary:
	return {
		"os": OS.get_name(),
		"osVersion": OS.get_version(),
		"cpu": OS.get_processor_name(),
		"cpuCores": OS.get_processor_count(),
		"memoryMiB": int(OS.get_memory_info().get("physical", -1)) / (1024 * 1024),
		"gpu": RenderingServer.get_video_adapter_name(),
		"gpuVendor": RenderingServer.get_video_adapter_vendor(),
		"gpuDriver": " ".join(OS.get_video_adapter_driver_info()),
		"graphicsApi": RenderingServer.get_video_adapter_api_version(),
		"renderer": RenderingServer.get_current_rendering_method(),
		"windowSize": str(DisplayServer.window_get_size()),
		"windowMode": DisplayServer.window_get_mode(),
		"screenScale": DisplayServer.screen_get_scale(),
	}


static func _performance() -> Dictionary:
	return {
		"fps": Engine.get_frames_per_second(),
		"processMs": Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0,
		"physicsMs": Performance.get_monitor(Performance.TIME_PHYSICS_PROCESS) * 1000.0,
		"drawCalls": Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME),
		"objects": Performance.get_monitor(Performance.OBJECT_COUNT),
		"nodes": Performance.get_monitor(Performance.OBJECT_NODE_COUNT),
		"staticMemoryMiB": Performance.get_monitor(Performance.MEMORY_STATIC) / (1024 * 1024),
		"videoMemoryMiB": Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / (1024 * 1024),
	}


static func _connection() -> Dictionary:
	var settings := SettingsManager.get_instance()
	return {
		"authenticated": ConnectionManager.http_ticket() != "",
		"gameServer": "%s:%d" % [settings.game_server_host, settings.game_server_port],
		"apiServer": settings.api_server_url,
		"latencyMs": ConnectionManager.latency_msec,
		"jitterMs": ConnectionManager.latency_jitter_msec,
		"rttMs": ConnectionManager.rtt_ms,
		"serverTick": ConnectionManager.server_tick(),
		"lastError": ConnectionManager.last_connection_error,
	}


static func _game(viewport: Viewport) -> Dictionary:
	var game := {"masterId": master_id()}

	var entities := EntityManager.get_instance()
	if entities != null:
		game["masterEntityId"] = entities.get_owned_master_entity_id()
		game["controlledEntityId"] = entities.get_controlled_entity_id()
		var controlled := entities.get_controlled_entity()
		if controlled != null:
			game["controlledPosition"] = str(controlled.global_position)

	var mouse := MouseManager.get_instance()
	if mouse != null and mouse.selected_entity != null:
		game["selectedEntityId"] = mouse.selected_entity.get("entity_id")

	var camera := viewport.get_camera_3d()
	if camera != null:
		game["cameraPosition"] = str(camera.global_position)
		game["cameraRotationDegrees"] = str(camera.global_rotation_degrees)

	return game


## Every setting the player can change; nothing in them names the player.
static func _settings() -> Dictionary:
	var settings := SettingsManager.get_instance()
	var values := {}
	for property: Dictionary in settings.get_property_list():
		if property.usage & PROPERTY_USAGE_SCRIPT_VARIABLE:
			values[property.name] = str(settings.get(property.name))
	return values
