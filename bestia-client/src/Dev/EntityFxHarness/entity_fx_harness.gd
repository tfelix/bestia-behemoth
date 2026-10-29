extends Node3D
## A stage for tuning entity effects: a master and the placeholder bestia, each with its own [EntityFx], and a
## panel to play, slow down, scrub and hand-drive every knob, with the weather switchable underneath.
##
## Interactive:
##   godot --path bestia-client/src res://Dev/EntityFxHarness/EntityFxHarness.tscn
## Screenshots, one per shot, then quit. A shot is <name>:<fx kind>:<progress 0..1>[:<weather>]:
##   DISPLAY=:0 godot --path bestia-client/src res://Dev/EntityFxHarness/EntityFxHarness.tscn -- \
##     --out=/tmp/fx --shots=mid:WING_VANISH:0.5,snow:HOME_VANISH:0.6:snow

const MasterVisualScn := preload("res://Game/Entity/Visual/MasterVisual/MasterVisual.tscn")
const BestiaVisualScn := preload("res://Game/Entity/Visual/BestiaVisual/BestiaVisual.tscn")

const _TORCH_ITEM_ID := 35
const _IRON_SWORD_ITEM_ID := 19
const _WEATHER_KINDS := {"dry": 0, "rain": 3, "snow": 6}

## A hand-made look to lay under the effects, to see how a status tint combines with a teleport.
var _sustained_tint: EntityFxDef = null

var _bodies: Array[EntityFx] = []
var _defs: Array[EntityFxDef] = []
var _def_picker: OptionButton
var _scrub: HSlider
var _manual := {}
var _manual_on: CheckBox
var _loop: CheckBox
var _weather := "dry"


func _ready() -> void:
	_build_stage()
	_bodies.append(_spawn_master(Vector3(-0.8, 0, 0)))
	_bodies.append(_spawn_bestia(Vector3(0.9, 0, 0)))
	_defs = EntityFxDB.get_instance().all()
	_sustained_tint = _make_tint()

	var shots := _arg("shots")
	if shots != "":
		_run_shots(_arg("out"), shots.split(","))
		return
	_build_panel()


func _arg(key: String) -> String:
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--%s=" % key):
			return arg.substr(key.length() + 3)
	return ""


# ---------------------------------------------------------------- stage

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
	plane.size = Vector2(12, 12)
	ground.mesh = plane
	var ground_material := StandardMaterial3D.new()
	ground_material.albedo_color = Color(0.32, 0.36, 0.3)
	ground.material_override = ground_material
	add_child(ground)

	var camera := Camera3D.new()
	camera.position = Vector3(0, 1.3, 4.2)
	camera.rotation_degrees = Vector3(-10, 0, 0)
	add_child(camera)


## Each body sits under its own host, as under an [Entity], so [EntityFx] can find the body's place.
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

	var gear := WornGearComponentSMSG.new()
	gear.ItemIds = [_IRON_SWORD_ITEM_ID, _TORCH_ITEM_ID]
	gear.LightLit = true
	visual.update_worn_gear(gear)
	visual.update_animation_from_client("idle")
	ConnectionManager.weather.ExposeToWeather(visual, true)
	return _attach_fx(host, visual)


func _spawn_bestia(at: Vector3) -> EntityFx:
	var host := _host(at)
	var visual: Visual = BestiaVisualScn.instantiate()
	visual.setup_visual(VisualComponentSMSG.new())
	host.add_child(visual)
	ConnectionManager.weather.ExposeToWeather(visual, true)
	return _attach_fx(host, visual)


func _set_weather(kind: String) -> void:
	_weather = kind
	var weather = ConnectionManager.weather
	weather.DebugOverrideEnabled = true
	weather.DebugKind = _WEATHER_KINDS[kind]
	weather.DebugIntensity = 1.0
	weather.DebugTemperatureCelsius = -5.0 if kind == "snow" else 12.0
	weather.DebugGroundWetness = 1.0 if kind == "rain" else 0.0
	weather.DebugSnowLine = -500.0 if kind == "snow" else 6000.0
	weather.DebugSnowFull = -400.0 if kind == "snow" else 6000.0


func _make_tint() -> EntityFxDef:
	var tint := EntityFxDef.new()
	tint.duration = 0.3
	tint.tint_colour = Color(0.45, 1.0, 0.4)
	tint.tint_strength = _flat_curve(0.7)
	tint.rim_colour = Color(0.4, 1.0, 0.3)
	tint.rim = _flat_curve(0.6)
	return tint


func _flat_curve(value: float) -> Curve:
	var curve := Curve.new()
	curve.add_point(Vector2(0, value))
	curve.add_point(Vector2(1, value))
	return curve


# ---------------------------------------------------------------- panel

func _build_panel() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var panel := PanelContainer.new()
	panel.position = Vector2(12, 12)
	layer.add_child(panel)
	var box := VBoxContainer.new()
	box.custom_minimum_size = Vector2(300, 0)
	panel.add_child(box)

	_def_picker = OptionButton.new()
	for def in _defs:
		_def_picker.add_item(def.kind)
	box.add_child(_def_picker)

	var buttons := HBoxContainer.new()
	box.add_child(buttons)
	_button(buttons, "Play", _play)
	_button(buttons, "Vanish + appear", _play_pair)
	_loop = CheckBox.new()
	_loop.text = "Loop"
	buttons.add_child(_loop)

	_slider(box, "Slow motion", 0.05, 1.0, 1.0, func(value): Engine.time_scale = value)
	_scrub = _slider(box, "Scrub", 0.0, 1.0, 0.0, _on_scrub)

	var sustain := CheckBox.new()
	sustain.text = "Sustain a status tint underneath"
	sustain.toggled.connect(_on_sustain)
	box.add_child(sustain)

	var weather := OptionButton.new()
	for kind in _WEATHER_KINDS:
		weather.add_item(kind)
	weather.item_selected.connect(func(index): _set_weather(weather.get_item_text(index)))
	box.add_child(weather)

	box.add_child(HSeparator.new())
	_manual_on = CheckBox.new()
	_manual_on.text = "Drive knobs by hand"
	_manual_on.toggled.connect(func(_on): _apply_manual())
	box.add_child(_manual_on)
	for knob in ["silhouette", "glow", "dissolve", "rim", "flash", "tint_strength"]:
		_manual[knob] = _slider(box, knob, 0.0, 1.0, 0.0, func(_value): _apply_manual())
	var directions := OptionButton.new()
	for direction in ["up", "down", "sideways"]:
		directions.add_item(direction)
	directions.item_selected.connect(func(_index): _apply_manual())
	_manual["direction"] = directions
	box.add_child(directions)

	for fx in _bodies:
		fx.finished.connect(_on_finished)


func _button(parent: Control, text: String, pressed: Callable) -> void:
	var button := Button.new()
	button.text = text
	button.pressed.connect(pressed)
	parent.add_child(button)


func _slider(parent: Control, label: String, low: float, high: float, value: float, changed: Callable) -> HSlider:
	var title := Label.new()
	title.text = label
	parent.add_child(title)
	var slider := HSlider.new()
	slider.min_value = low
	slider.max_value = high
	slider.step = 0.01
	slider.value = value
	slider.value_changed.connect(changed)
	parent.add_child(slider)
	return slider


func _selected() -> EntityFxDef:
	return _defs[_def_picker.selected] if _def_picker.selected >= 0 else null


func _play() -> void:
	_manual_on.button_pressed = false
	for fx in _bodies:
		fx.play(_selected())


## Plays the picked effect's vanish, then its appear, as a teleport does.
func _play_pair() -> void:
	var vanish := _selected()
	var appear := EntityFxDB.get_instance().get_fx(vanish.kind.replace("VANISH", "APPEAR"))
	for fx in _bodies:
		fx.play(vanish)
	await get_tree().create_timer(vanish.duration + 0.15).timeout
	for fx in _bodies:
		fx.play(appear)


func _on_finished(def: EntityFxDef) -> void:
	if _loop.button_pressed and def == _selected() and not def.hold_end:
		_play()
	elif _loop.button_pressed and def.hold_end:
		await get_tree().create_timer(0.4).timeout
		_play()


func _on_scrub(progress: float) -> void:
	for fx in _bodies:
		fx.pose(_selected(), progress)


func _on_sustain(on: bool) -> void:
	for fx in _bodies:
		if on:
			fx.sustain(&"harness_tint", _sustained_tint)
		else:
			fx.release(&"harness_tint")


func _apply_manual() -> void:
	if not _manual_on.button_pressed:
		for fx in _bodies:
			fx.release(&"manual")
		return
	var def := EntityFxDef.new()
	def.duration = 0.01
	for knob in ["silhouette", "glow", "dissolve", "rim", "flash", "tint_strength"]:
		def.set(knob, _flat_curve(_manual[knob].value))
	def.dissolve_direction = [Vector3.UP, Vector3.DOWN, Vector3.RIGHT][_manual["direction"].selected]
	def.tint_colour = Color(1.0, 0.35, 0.3)
	def.rim_colour = Color(1.0, 0.9, 0.6)
	for fx in _bodies:
		fx.sustain(&"manual", def)


# ---------------------------------------------------------------- shots

func _run_shots(out_dir: String, shots: PackedStringArray) -> void:
	DirAccess.make_dir_recursive_absolute(out_dir)
	var readout: CanvasLayer = RenderDebug.get("_layer")
	if readout != null:
		readout.visible = false

	# The placeholder bestia fades in on its own first.
	await get_tree().create_timer(1.2).timeout
	for shot in shots:
		var parts := shot.split(":")
		_set_weather(parts[3] if parts.size() > 3 else "dry")
		var def := EntityFxDB.get_instance().get_fx(parts[1])
		var progress := float(parts[2])
		await _frames(30)
		# Played in slow motion rather than posed, so the streaks are mid-flight as they would be.
		Engine.time_scale = 0.05
		for fx in _bodies:
			fx.play(def)
		var played := 0.0
		while played < progress * def.duration:
			await get_tree().process_frame
			played += get_process_delta_time()
		var path := out_dir.path_join(parts[0] + ".png")
		get_viewport().get_texture().get_image().save_png(path)
		print("[fx-harness] wrote ", path)
		Engine.time_scale = 1.0
		for fx in _bodies:
			fx.clear()
	get_tree().quit()


func _frames(count: int) -> void:
	for i in count:
		await get_tree().process_frame
