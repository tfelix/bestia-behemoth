extends Node3D
## Arrows shot between two placeholder bestias, to judge the tracer's size, arc and speed.
##
## Interactive, one volley a second:
##   godot --path bestia-client/src res://Dev/ArrowHarness/ArrowHarness.tscn
## One screenshot of a volley in flight, then quit:
##   DISPLAY=:0 godot --path bestia-client/src --fixed-fps 60 res://Dev/ArrowHarness/ArrowHarness.tscn -- --out=/tmp/arrows.png [--tiles=5]

const ArrowTracerScn = preload("res://Game/Entity/Visual/Projectile/ArrowTracer.tscn")

## One arrow per frame offset, so a single screenshot shows the whole flight at once.
const _VOLLEY_FRAME_GAP := 3
const _VOLLEY_SIZE := 4

var _from: Vector3
var _to: Vector3
var _frame := 0


func _ready() -> void:
	var tiles := float(_arg("tiles")) if _arg("tiles") != "" else 5.0
	_from = Vector3(-tiles / 2.0, 1.2, 0)
	_to = Vector3(tiles / 2.0, 0.8, 0)
	_build_stage(tiles)


func _physics_process(_delta: float) -> void:
	_frame += 1
	var in_volley := _frame % 60
	if in_volley < _VOLLEY_SIZE * _VOLLEY_FRAME_GAP and in_volley % _VOLLEY_FRAME_GAP == 0:
		var arrow: ArrowTracer = ArrowTracerScn.instantiate()
		add_child(arrow)
		arrow.fly(_from, _to)

	var out := _arg("out")
	if out != "" and _frame == 60 + _VOLLEY_SIZE * _VOLLEY_FRAME_GAP:
		get_viewport().get_texture().get_image().save_png(out)
		print("[arrow-harness] wrote ", out)
		get_tree().quit()


func _arg(key: String) -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--%s=" % key):
			return arg.substr(key.length() + 3)
	return ""


func _build_stage(tiles: float) -> void:
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color(0.16, 0.18, 0.22)
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color(0.55, 0.58, 0.65)
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
	plane.size = Vector2(tiles + 6, 6)
	ground.mesh = plane
	var ground_material := StandardMaterial3D.new()
	ground_material.albedo_color = Color(0.32, 0.36, 0.3)
	ground.material_override = ground_material
	add_child(ground)

	_add_body(Vector3(_from.x, 0, 0), Color(0.25, 0.6, 0.95))
	_add_body(Vector3(_to.x, 0, 0), Color(0.85, 0.2, 0.2))

	var camera := Camera3D.new()
	camera.position = Vector3(0, 2.2, tiles * 0.9 + 1.5)
	camera.rotation_degrees = Vector3(-14, 0, 0)
	add_child(camera)


func _add_body(at: Vector3, colour: Color) -> void:
	var body := MeshInstance3D.new()
	var capsule := CapsuleMesh.new()
	capsule.radius = 0.3
	capsule.height = 1.5
	body.mesh = capsule
	var material := StandardMaterial3D.new()
	material.albedo_color = colour
	body.material_override = material
	body.position = at + Vector3.UP * 0.75
	add_child(body)
