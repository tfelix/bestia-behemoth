extends Node3D
## A line of placeholder bestias, one per shape, next to the Doommaster, to judge how species without art
## tell apart.
##
## Interactive:
##   godot --path bestia-client/src res://Dev/PlaceholderHarness/PlaceholderHarness.tscn
## One screenshot, then quit:
##   DISPLAY=:0 godot --path bestia-client/src res://Dev/PlaceholderHarness/PlaceholderHarness.tscn -- --out=/tmp/placeholders.png

const _DOOMMASTER_ID := 2
const Shape := BestiaResource.PlaceholderShape

## Shape, colour, scale, glow.
const _LOOKS := [
	[Shape.SPHERE, Color(0.82, 0.55, 0.1), 1.0, 0.0],
	[Shape.CAPSULE, Color(0.25, 0.6, 0.95), 1.0, 0.0],
	[Shape.BOX, Color(0.45, 0.75, 0.25), 0.8, 0.0],
	[Shape.CYLINDER, Color(0.6, 0.3, 0.75), 1.2, 0.0],
	[Shape.PRISM, Color(0.85, 0.2, 0.2), 1.0, 0.0],
	[Shape.TORUS, Color(0.95, 0.85, 0.3), 0.9, 1.5],
]


func _ready() -> void:
	_build_stage()
	var x := -3.2
	for look in _LOOKS:
		var species := BestiaResource.new()
		species.placeholder_shape = look[0]
		species.placeholder_colour = look[1]
		species.placeholder_scale = look[2]
		species.placeholder_glow = look[3]
		_spawn(species, Vector3(x, 0, 0))
		x += 1.15
	_spawn(BestiaDB.get_instance().get_bestia(_DOOMMASTER_ID), Vector3(x + 0.2, 0, 0))

	var out := _arg("out")
	if out != "":
		await get_tree().create_timer(1.5).timeout
		get_viewport().get_texture().get_image().save_png(out)
		print("[placeholder-harness] wrote ", out)
		get_tree().quit()


func _spawn(species: BestiaResource, at: Vector3) -> void:
	var visual: Node3D = species.get_bestia_visual().instantiate()
	visual.apply_placeholder(species)
	visual.position = at
	add_child(visual)
	ConnectionManager.weather.ExposeToWeather(visual, true)


func _arg(key: String) -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--%s=" % key):
			return arg.substr(key.length() + 3)
	return ""


func _build_stage() -> void:
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color(0.16, 0.18, 0.22)
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color(0.55, 0.58, 0.65)
	environment.glow_enabled = true
	environment.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	var world := WorldEnvironment.new()
	world.environment = environment
	add_child(world)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-50, 35, 0)
	sun.shadow_enabled = true
	add_child(sun)

	var ground := MeshInstance3D.new()
	var plane := PlaneMesh.new()
	plane.size = Vector2(16, 10)
	ground.mesh = plane
	var ground_material := StandardMaterial3D.new()
	ground_material.albedo_color = Color(0.32, 0.36, 0.3)
	ground.material_override = ground_material
	add_child(ground)

	var camera := Camera3D.new()
	camera.position = Vector3(0.5, 1.7, 4.6)
	camera.rotation_degrees = Vector3(-12, 0, 0)
	add_child(camera)
