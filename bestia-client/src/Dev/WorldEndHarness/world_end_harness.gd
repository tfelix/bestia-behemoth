extends Node3D
## A stage for the world's final sequence: a master and the placeholder bestia under the [code]WorldEnd[/code]
## overlay, playing a made-up summary.
##
## Interactive, with buttons to play either ending:
##   godot --path bestia-client/src res://Dev/WorldEndHarness/WorldEndHarness.tscn
## Screenshots at the given seconds into the sequence, then quit:
##   DISPLAY=:0 godot --path bestia-client/src res://Dev/WorldEndHarness/WorldEndHarness.tscn -- \
##     --ending=lost --out=/tmp/end --shots=2,5,8,16

const MasterVisualScn := preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
const BestiaVisualScn := preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")
const WorldEndScn := preload("res://Game/UI/WorldEnd/WorldEnd.tscn")

var _bodies: Array[EntityFx] = []
var _overlay: Control


func _ready() -> void:
	_build_stage()
	_bodies.append(_spawn_master(Vector3(-0.8, 0, 0)))
	_bodies.append(_spawn_bestia(Vector3(0.9, 0, 0)))

	var layer := CanvasLayer.new()
	add_child(layer)
	_overlay = WorldEndScn.instantiate()
	layer.add_child(_overlay)

	var shots := _arg("shots")
	if shots != "":
		_run_shots(_arg("ending"), _arg("out"), shots.split(","))
		return
	_build_panel(layer)


func _arg(key: String) -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--%s=" % key):
			return arg.substr(key.length() + 3)
	return ""


func _play(ending: String) -> void:
	var message := WorldEndSMSG.new()
	message.EndingName = ending
	message.WorldName = "Genesis"
	message.Threat = "VOID_TEAR"
	message.DaysLived = 412
	message.TPercent = 103 if ending == "lost" else 150
	message.TownsLost = 7
	message.SecondsLeft = 90
	_overlay.play(message, _bodies)


func _run_shots(ending: String, out_dir: String, shots: PackedStringArray) -> void:
	DirAccess.make_dir_recursive_absolute(out_dir)
	# The placeholder bestia fades in on its own first.
	await get_tree().create_timer(1.2).timeout
	_play(ending)
	var played := 0.0
	for shot in shots:
		var at := float(shot)
		await get_tree().create_timer(at - played).timeout
		played = at
		var path := out_dir.path_join("%s_%05.1f.png" % [ending, at])
		get_viewport().get_texture().get_image().save_png(path)
		print("[world-end-harness] wrote ", path)
	get_tree().quit()


func _build_panel(layer: CanvasLayer) -> void:
	var row := HBoxContainer.new()
	row.position = Vector2(12, 12)
	layer.add_child(row)
	for ending in ["lost", "saved"]:
		var button := Button.new()
		button.text = "Play " + ending
		button.pressed.connect(_play.bind(ending))
		row.add_child(button)


func _build_stage() -> void:
	var environment := Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.background_color = Color(0.45, 0.6, 0.78)
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
	plane.size = Vector2(40, 40)
	ground.mesh = plane
	var ground_material := StandardMaterial3D.new()
	ground_material.albedo_color = Color(0.3, 0.42, 0.24)
	ground.material_override = ground_material
	add_child(ground)

	var camera := Camera3D.new()
	camera.position = Vector3(0, 1.6, 4.6)
	camera.rotation_degrees = Vector3(-12, 0, 0)
	add_child(camera)


## Each body sits under its own host, as under an [Entity], so [EntityFx] can find the body's place.
func _spawn_master(at: Vector3) -> EntityFx:
	var host := _host(at)
	var look := MasterVisualComponentSMSG.new()
	look.Name = "Harness"
	look.HairColor = Color(0.35, 0.2, 0.1)
	look.SkinColor = Color(0.96, 0.8, 0.68)
	look.Hair = 1
	var visual: MasterVisual = MasterVisualScn.instantiate()
	visual.setup_visual(look)
	host.add_child(visual)
	visual.update_animation_from_client("idle")
	return _attach_fx(host, visual)


func _spawn_bestia(at: Vector3) -> EntityFx:
	var host := _host(at)
	var visual: Visual = BestiaVisualScn.instantiate()
	visual.setup_visual(VisualComponentSMSG.new())
	host.add_child(visual)
	return _attach_fx(host, visual)


func _host(at: Vector3) -> Node3D:
	var host := Node3D.new()
	host.position = at
	add_child(host)
	return host


func _attach_fx(host: Node3D, visual: Visual) -> EntityFx:
	var fx := EntityFx.new()
	host.add_child(fx)
	fx.bind(visual)
	return fx
