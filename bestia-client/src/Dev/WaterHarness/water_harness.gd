extends Node3D

## Renders a pond with rocks in it under fixed weathers, to judge the water shader.
##
## Run it with a window - the headless renderer draws nothing:
##   DISPLAY=:0 godot --path bestia-client/src res://Dev/WaterHarness/WaterHarness.tscn -- --out=DIR [--shots=a,b]
##     [--set=reflection_strength=1.0,storm_tint=0] [--no-gi] [--stepped] [--chunks=FILE]
##
## --no-gi turns SDFGI off. Over this bare, bright plane it overexposes a clear noon until it has settled.
##
## --stepped terraces the bed the way the voxel world does, so the shore is a short step rather than a slope.
##
## --chunks meshes a dump taken in game with F7 (see render_debug.gd) through the real terrain renderer instead
## of building the pond, and shoots it from the camera the dump was taken from; the close shot moves two thirds
## of the way in toward what that camera looked at. For faults only the world's own ground shows.
##
## The pond is built here rather than streamed, so the shot needs no server and no generated world.

## WeatherSMSG.Kind ordinals. The C# enum is nested in the message class, where GDScript cannot name it.
const CLEAR := 0
const CLOUDY := 1
const THUNDERSTORM := 5

## name, hour, weather kind, cloud cover, wind m/s.
const SHOTS := [
	["sunny", 12.0, CLEAR, 0.1, 2.0],
	["overcast", 12.0, CLOUDY, 0.7, 7.0],
	# What a live server sent on 2026-10-27: a dry grey day with a gale under it.
	["cloudy_gale", 12.0, CLOUDY, 0.71, 20.0],
	["storm", 12.0, THUNDERSTORM, 1.0, 18.0],
]

const WARMUP_FRAMES := 300
const SETTLE_FRAMES := 200

const SECONDS_PER_DAY := 24 * 3600

const POND_RADIUS := 10.0
const POND_DEPTH := 2.2
const GROUND_SIZE := 80.0
const GROUND_CELL := 0.5

const SAND := Color(0.66, 0.56, 0.38)
const GRASS := Color(0.3, 0.46, 0.18)

## Centre, radius. Most of each rock sits under the surface, as in a real pond.
const ROCKS := [
	[Vector3(-6.0, -0.35, -1.5), 1.3],
	[Vector3(-4.2, -0.3, -2.5), 0.9],
	[Vector3(-5.2, -0.45, 0.4), 0.9],
	[Vector3(-2.0, -0.4, -6.0), 0.8],
	[Vector3(-0.8, -0.4, -5.2), 0.6],
	[Vector3(0.0, -0.4, -0.5), 1.4],
	[Vector3(3.5, -0.4, -4.5), 0.6],
	[Vector3(5.6, -0.5, -6.2), 1.1],
	[Vector3(4.6, -0.45, 0.2), 1.1],
	[Vector3(6.6, -0.4, 1.2), 0.55],
	[Vector3(6.4, -0.45, 2.8), 0.95],
]

var _out_dir := "user://water_harness"
var _only: PackedStringArray = []
var _warmup := WARMUP_FRAMES
var _settle := SETTLE_FRAMES
var _overrides := {}
var _stepped := false
var _chunks := ""

@onready var _pond_camera: Camera3D = $PondCamera
@onready var _close_camera: Camera3D = $CloseCamera


func _ready() -> void:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--out="):
			_out_dir = arg.trim_prefix("--out=")
		elif arg.begins_with("--shots="):
			_only = arg.trim_prefix("--shots=").split(",")
		elif arg.begins_with("--frames="):
			_warmup = int(arg.trim_prefix("--frames="))
			_settle = _warmup
		elif arg.begins_with("--chunks="):
			_chunks = arg.trim_prefix("--chunks=")
		elif arg == "--stepped":
			_stepped = true
		elif arg.begins_with("--set="):
			for pair in arg.trim_prefix("--set=").split(","):
				var parts := pair.split("=")
				_overrides[parts[0]] = float(parts[1])

	if _chunks.is_empty():
		_build_ground()
		_build_rocks()
		_build_trees()
		_build_water()

		# The pond camera stands where the reference shot was taken from: high on the far bank, looking across.
		_pond_camera.look_at_from_position(Vector3(0, 9.0, 15.0), Vector3(0, 0, -2.0))
		_close_camera.look_at_from_position(Vector3(2.0, 3.0, 4.0), Vector3(-0.5, 0, -1.0))
	else:
		_load_chunks()

	if OS.get_cmdline_user_args().has("--no-gi"):
		($Environment/WorldEnvironment as WorldEnvironment).environment.sdfgi_enabled = false

	DirAccess.make_dir_recursive_absolute(_out_dir)
	_run.call_deferred()


func _run() -> void:
	var first := true

	for shot in SHOTS:
		if not _only.is_empty() and not _only.has(shot[0]):
			continue

		print("[water-harness] shot ", shot[0])
		_apply(shot)
		await _frames(_warmup if first else _settle)
		first = false

		await _capture(_pond_camera, "%s_pond" % shot[0])
		await _capture(_close_camera, "%s_close" % shot[0])

	get_tree().quit()


func _apply(shot: Array) -> void:
	var info := WorldInfoSMSG.new()
	info.WorldAgeBestiaSeconds = 14.0 * SECONDS_PER_DAY + shot[1] * 3600.0
	info.TimeSpeedFactor = 0.0001
	info.HoursPerDay = 24
	info.DaysPerMonth = 30
	info.MonthsPerYear = 4
	info.NightEndHour = 4
	info.DawnEndHour = 6
	info.DuskStartHour = 20
	info.NightStartHour = 22
	ConnectionManager.world_clock.Anchor(info)

	var weather: Node = ConnectionManager.weather
	weather.DebugOverrideEnabled = true
	weather.DebugKind = shot[2]
	weather.DebugIntensity = 0.8
	weather.DebugCloudCover = shot[3]
	weather.DebugWindSpeed = shot[4]
	weather.DebugWindDirectionDegrees = 30.0

	# A shot shows the weather it asked for, not one still easing in from the previous shot.
	weather.SkySeconds = 0.2
	weather.WindSeconds = 0.2
	weather.WindSpeedSeconds = 0.2
	weather.WettingSeconds = 0.2
	weather.DryingSeconds = 0.2

	$Environment.set("_last_updated_since", 1.0e9)


## Meshes a chunk dump through the real terrain renderer, and puts both cameras on the view it was taken from.
func _load_chunks() -> void:
	var terrain: Node3D = load("res://Game/World/TerrainRenderer.cs").new()
	terrain.name = "Terrain"
	add_child(terrain)
	var view: Dictionary = terrain.call("LoadChunkDump", _chunks)

	# The renderer loads the shared water.tres, so the overrides go on that one rather than on a copy.
	var water: ShaderMaterial = load("res://Game/World/Shader/water.tres")
	for parameter in _overrides:
		water.set_shader_parameter(parameter, _overrides[parameter])

	var camera: Transform3D = view["camera"]
	for each in [_pond_camera, _close_camera]:
		each.fov = view["fov"]
		each.far = 2000.0

	_pond_camera.global_transform = camera

	# In along the view to the water plane, the server's sea level at y = 0, and two thirds of the way there.
	var forward := -camera.basis.z
	var reach := camera.origin.y / maxf(-forward.y, 0.05)
	_close_camera.global_transform = camera.translated(forward * reach * 0.66)


## A sand bowl that rises into a grass plain, with the shore wobbled so it is not a circle.
func _build_ground() -> void:
	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)

	var cells := int(GROUND_SIZE / GROUND_CELL)
	var origin := -GROUND_SIZE * 0.5

	for z in cells:
		for x in cells:
			var corners := [
				Vector2(origin + x * GROUND_CELL, origin + z * GROUND_CELL),
				Vector2(origin + (x + 1) * GROUND_CELL, origin + z * GROUND_CELL),
				Vector2(origin + (x + 1) * GROUND_CELL, origin + (z + 1) * GROUND_CELL),
				Vector2(origin + x * GROUND_CELL, origin + (z + 1) * GROUND_CELL),
			]
			for index in [0, 1, 2, 0, 2, 3]:
				var point: Vector2 = corners[index]
				var height := _ground_height(point)
				tool.set_color(SAND.lerp(GRASS, smoothstep(0.35, 0.6, height)))
				tool.add_vertex(Vector3(point.x, height, point.y))

	tool.generate_normals()

	var material := StandardMaterial3D.new()
	material.vertex_color_use_as_albedo = true
	material.roughness = 1.0

	var ground := MeshInstance3D.new()
	ground.mesh = tool.commit()
	ground.material_override = material
	add_child(ground)


func _ground_height(point: Vector2) -> float:
	# Wider than deep, as the reference pond is, and wobbled so the shore is not a contour of a circle.
	var stretched := Vector2(point.x / 1.25, point.y)
	var wobble := 0.6 * sin(atan2(stretched.y, stretched.x) * 3.0) + 0.3 * sin(atan2(stretched.y, stretched.x) * 7.0)
	var distance := stretched.length() + wobble

	var height := -POND_DEPTH + (POND_DEPTH + 0.7) * smoothstep(POND_RADIUS * 0.3, POND_RADIUS * 1.15, distance)
	if not _stepped:
		return height

	# The streamed world's shore: one-metre voxels on a shelf rising a metre per hundred lay the bed in flat
	# terraces, so the water stays a voxel deep right up to a short step at the waterline.
	var level := floorf(height + 0.75)
	return level - 0.75 + smoothstep(0.8, 1.0, height + 0.75 - level)


func _build_rocks() -> void:
	var material := StandardMaterial3D.new()
	material.albedo_color = Color(0.42, 0.43, 0.5)
	material.roughness = 0.9

	var random := RandomNumberGenerator.new()
	random.seed = 7

	for rock in ROCKS:
		var instance := MeshInstance3D.new()
		instance.mesh = _rock_mesh(rock[1], random)
		instance.material_override = material
		instance.position = rock[0]
		instance.rotation.y = random.randf() * TAU
		add_child(instance)


## Trees on the far bank: tall things for the water to reflect, which the low rocks barely are.
func _build_trees() -> void:
	var bark := StandardMaterial3D.new()
	bark.albedo_color = Color(0.4, 0.26, 0.15)
	var leaves := StandardMaterial3D.new()
	leaves.albedo_color = Color(0.25, 0.48, 0.2)

	for spot in [Vector3(-9.0, 0.5, -10.5), Vector3(-2.5, 0.5, -12.0), Vector3(7.5, 0.5, -10.0)]:
		var trunk := MeshInstance3D.new()
		var trunk_mesh := CylinderMesh.new()
		trunk_mesh.top_radius = 0.2
		trunk_mesh.bottom_radius = 0.35
		trunk_mesh.height = 3.0
		trunk.mesh = trunk_mesh
		trunk.material_override = bark
		trunk.position = spot + Vector3(0, 1.5, 0)
		add_child(trunk)

		var crown := MeshInstance3D.new()
		var crown_mesh := CylinderMesh.new()
		crown_mesh.top_radius = 0.0
		crown_mesh.bottom_radius = 1.8
		crown_mesh.height = 4.5
		crown_mesh.radial_segments = 7
		crown.mesh = crown_mesh
		crown.material_override = leaves
		crown.position = spot + Vector3(0, 5.0, 0)
		add_child(crown)


## A low-poly boulder: a coarse sphere with its corners pushed about, faceted rather than smooth.
func _rock_mesh(radius: float, random: RandomNumberGenerator) -> Mesh:
	var sphere := SphereMesh.new()
	sphere.radius = radius
	sphere.height = radius * 1.6
	sphere.radial_segments = 7
	sphere.rings = 4

	var source := sphere.get_mesh_arrays()
	var vertices: PackedVector3Array = source[Mesh.ARRAY_VERTEX]
	var indices: PackedInt32Array = source[Mesh.ARRAY_INDEX]

	# Pushed per position rather than per vertex, so the seam the sphere splits its UVs along stays closed.
	var pushes := {}
	for vertex in vertices:
		var key := Vector3i((vertex * 100.0).round())
		if not pushes.has(key):
			pushes[key] = 1.0 + random.randf_range(-0.15, 0.15)

	var tool := SurfaceTool.new()
	tool.begin(Mesh.PRIMITIVE_TRIANGLES)
	tool.set_smooth_group(-1)
	for index in indices:
		var vertex := vertices[index]
		tool.add_vertex(vertex * pushes[Vector3i((vertex * 100.0).round())])
	tool.generate_normals()

	return tool.commit()


func _build_water() -> void:
	var plane := PlaneMesh.new()
	plane.size = Vector2(GROUND_SIZE, GROUND_SIZE)
	plane.subdivide_width = 8
	plane.subdivide_depth = 8

	var water := MeshInstance3D.new()
	water.mesh = plane
	var material: ShaderMaterial = load("res://Game/World/Shader/water.tres").duplicate()
	for parameter in _overrides:
		material.set_shader_parameter(parameter, _overrides[parameter])
	water.material_override = material
	add_child(water)


func _capture(camera: Camera3D, name: String) -> void:
	camera.current = true

	# The render-debug readout is an overlay on the viewport, so it would land in every image.
	var readout: CanvasLayer = RenderDebug.get("_layer")
	if readout != null:
		readout.visible = false

	await _frames(3)

	var path := _out_dir.path_join(name + ".png")
	get_viewport().get_texture().get_image().save_png(path)
	print("[water-harness] wrote ", path)


func _frames(count: int) -> void:
	for i in count:
		await get_tree().process_frame
