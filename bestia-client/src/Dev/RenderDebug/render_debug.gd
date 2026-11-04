extends Node

## A debug panel of runtime switches for the things that can make a shadow appear to flicker while the camera
## moves, and of the frame-time readout, so that each one can be taken out of the picture without a restart, a
## scene edit or a rebuild. F1 opens and closes the panel.
##
## [b]Why this is a tool and not a fix.[/b] A shadow that flickers only while you move has several candidate
## causes in this client and they live in different files: the environment's SDFGI and its two screen-space
## passes, the sun's PSSM cascade blending, the grass field's per-frame level of detail, and the cloud shadow
## decals. Every one of them is still when the camera is still, which is what makes reading the code
## insufficient - the question is which one stops when you switch it off, and that is a question about a
## running game. So the panel is an A/B rig rather than a settings menu, and the readout in the top left exists
## so that a screenshot of a flicker records which of them were off when it was taken - it stays up with the
## panel closed.
##
## [b]F1 and nothing else.[/b] The rig once had a key per switch, F1 to F12. F5 to F8 belong to the editor's run
## bar in a game started from the editor - F8 stopped the game instead of showing frame times - and a dozen keys
## nobody remembers are worse than one panel that names its switches.
##
## [b]An instance of [code]RenderDebug.tscn[/code] in each scene where a flicker gets chased[/b] -
## [code]Game.tscn[/code], [code]Dev/TerrainTestbed[/code] and [code]Dev/GrassBench[/code] - and nothing in the
## game calls it. The panel's controls are wired as exports; the world nodes it switches are found by walking the
## current scene each time, since the scene under it is a different one in each of the three.
##
## [b]Gone outside a debug build.[/b] The node frees itself unless [method OS.is_debug_build], so an exported
## release has no input handler, no panel and no per-frame cost.

## The script that draws the decorative grass field, found by path rather than by class name.
##
## [code]TerrainGrass[/code] is a C# [code]GlobalClass[/code], so [code]is[/code] would very likely work -
## but [code]game.gd[/code] reaches the same class through [method @GDScript.preload] rather than by its
## global name, and a debug tool is a poor place to be the first to depend on that registration.
const _GRASS_SCRIPT := "res://Game/World/TerrainGrass.cs"

const _CLOUD_SCRIPT := "res://Game/Environment/CloudShadows/cloud_shadows.gd"

const _TERRAIN_SCRIPT := "res://Game/World/TerrainRenderer.cs"

## The view modes the panel offers, in the order they answer questions.
##
## A curated list rather than the whole of [enum Viewport.DebugDraw], most of which is about lights and
## occluders this world does not use. What each one here is for:
##
## - [b]unshaded[/b] is the first test worth running: no lights, no shadows, no GI and no screen-space
##   passes, but the geometry is still there. If dark patches still churn, the flicker is the grass field
##   rearranging itself and not a shadow at all.
## - [b]lighting[/b] is the mirror of it - diffuse light with the albedo taken away - which isolates the
##   shadow from the ground it falls on.
## - [b]PSSM splits[/b] tints each shadow cascade, so where the split boundaries fall relative to the trees
##   stops being arithmetic off [code]directional_shadow_split_1[/code] and becomes something visible.
## - [b]SSAO[/b], [b]SSIL[/b] and [b]GI buffer[/b] show those three contributions alone. A pass that is
##   flickering flickers far more plainly on its own than it does mixed into a lit frame.
## - [b]SDFGI probes[/b] and [b]SDFGI cascades[/b] show the probe grid re-centring as you walk, which is
##   what a re-converge looks like from the outside.
## - [b]overdraw[/b] reads the grass density directly, and [b]no LOD[/b] pins every mesh at its finest level
##   so that a mesh LOD swap can be ruled out.
const _DRAW_MODES: Array[int] = [
	Viewport.DEBUG_DRAW_DISABLED,
	Viewport.DEBUG_DRAW_UNSHADED,
	Viewport.DEBUG_DRAW_LIGHTING,
	Viewport.DEBUG_DRAW_PSSM_SPLITS,
	Viewport.DEBUG_DRAW_SSAO,
	Viewport.DEBUG_DRAW_SSIL,
	Viewport.DEBUG_DRAW_GI_BUFFER,
	Viewport.DEBUG_DRAW_SDFGI_PROBES,
	Viewport.DEBUG_DRAW_SDFGI,
	Viewport.DEBUG_DRAW_OVERDRAW,
	Viewport.DEBUG_DRAW_DISABLE_LOD,
]

const _DRAW_NAMES: Array[String] = [
	"normal",
	"unshaded",
	"lighting",
	"PSSM splits",
	"SSAO",
	"SSIL",
	"GI buffer",
	"SDFGI probes",
	"SDFGI cascades",
	"overdraw",
	"no LOD",
]


@export var readout: Control
@export var readout_label: Label
@export var menu: Control
@export var view_mode: OptionButton
@export var sdfgi: BaseButton
@export var ssil: BaseButton
@export var ssao: BaseButton
@export var grass_budget: BaseButton
@export var blend_splits: BaseButton
@export var cloud_shadows: BaseButton
@export var frame_times: BaseButton
@export var dump_chunks: BaseButton

## The grass budget the switch took away, or -1 while the field is running on its own.
var _saved_budget := -1

## Whether the frame-time line is showing. It is the one line here that is a measurement, not a switch.
var _stats := false

## Seconds since the readout and the panel were last brought up to date. A number that changes every frame
## cannot be read.
var _since_refresh := 0.0

## The scene [member _found] was filled from, and what [method _find] found in it by key.
var _scene: Node = null
var _found := {}


func _ready() -> void:
	# Nothing here should survive into a release export - not the input handler, and not a panel that could be
	# brought up in front of a player.
	if not OS.is_debug_build():
		queue_free()
		return

	# Printed rather than only shown, because the panel starts closed and a key nobody knows about is a key
	# nobody presses.
	print("[render debug] F1 opens the render debug panel")

	for mode_name in _DRAW_NAMES:
		view_mode.add_item(mode_name)

	view_mode.item_selected.connect(_set_draw)
	sdfgi.toggled.connect(_set_gi.bind("sdfgi_enabled", "sdfgi"))
	ssil.toggled.connect(_set_gi.bind("ssil_enabled", "ssil"))
	ssao.toggled.connect(_set_gi.bind("ssao_enabled", "ssao"))
	grass_budget.toggled.connect(_set_grass_budget)
	blend_splits.toggled.connect(_set_blend_splits)
	cloud_shadows.toggled.connect(_set_cloud_shadows)
	frame_times.toggled.connect(_set_stats)
	dump_chunks.pressed.connect(_dump_chunks)

	set_process(false)
	_refresh()


## F1 is the only key claimed, and it is marked handled.
##
## [method Node._input] rather than [method Node._unhandled_input], which is the one deliberate rudeness here:
## [code]_unhandled_input[/code] runs after the UI has had its turn, so a focused chat box would eat it.
## Debugging a flicker while the chat happens to be open is exactly when that would be most annoying to discover.
func _input(event: InputEvent) -> void:
	var key := event as InputEventKey
	if key == null or not key.pressed or key.echo:
		return

	# Physical, so the rig works on a keyboard layout this repo has not been opened on.
	if key.physical_keycode != KEY_F1:
		return

	get_viewport().set_input_as_handled()
	menu.visible = not menu.visible
	_update_processing()
	_refresh()


## Runs [method _process] only while something in it is live: the panel, so its switches follow what the scene
## did on its own, or the frame-time line.
func _update_processing() -> void:
	set_process(menu.visible or _stats)


func _process(delta: float) -> void:
	_since_refresh += delta
	if _since_refresh >= 0.25:
		_since_refresh = 0.0
		_refresh()


func _set_draw(index: int) -> void:
	get_viewport().debug_draw = _DRAW_MODES[index]
	print("[render debug] view mode: %s" % _DRAW_NAMES[index])
	_refresh()


func _set_gi(on: bool, property: StringName, label: String) -> void:
	var env := _environment()
	if env == null:
		return

	env.set(property, on)
	print("[render debug] %s: %s" % [label, "on" if on else "off"])
	_refresh()


## Takes the grass field's draw budget away, which takes its whole level-of-detail controller with it.
##
## [code]MaxVisibleTriangles[/code] of 0 means no budget, and that is a bigger switch than it sounds: with no
## budget [code]GrassLod.BudgetTrim[/code] returns 1 and [code]GrassLod.NextExponent[/code] returns 1, so
## the per-frame feedback loop that thins the far field is out of the picture. If the ground stops breathing
## with this off, the flicker was the field being retuned and not a shadow.
##
## It is also how to see the canopy on its own: with every tuft drawn the ground is hidden, and with the field
## switched off entirely what is left is what the terrain shader draws in its place.
##
## It costs frames, and is meant to: every tuft the field wanted is now drawn.
func _set_grass_budget(on: bool) -> void:
	var grass := _grass()
	if grass == null:
		return

	if not on and _saved_budget < 0:
		_saved_budget = grass.MaxVisibleTriangles
		grass.MaxVisibleTriangles = 0
		print("[render debug] grass draw budget: off (was %d)" % _saved_budget)
	elif on and _saved_budget >= 0:
		grass.MaxVisibleTriangles = _saved_budget
		print("[render debug] grass draw budget: %d" % _saved_budget)
		_saved_budget = -1

	_refresh()


## Shows what a frame costs and what the grass field is spending, so that its settings can be judged by the
## frame time on the machine at hand rather than by a triangle count.
func _set_stats(on: bool) -> void:
	_stats = on
	RenderingServer.viewport_set_measure_render_time(get_viewport().get_viewport_rid(), _stats)
	_update_processing()
	print("[render debug] frame times: %s" % ("on" if _stats else "off"))
	_refresh()


func _stats_lines() -> Array[String]:
	var viewport := get_viewport()
	var rid := viewport.get_viewport_rid()
	var lines: Array[String] = [
		"%d fps   cpu %.1f ms   gpu %.1f ms" % [
			Engine.get_frames_per_second(),
			RenderingServer.viewport_get_measured_render_time_cpu(rid)
				+ Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0,
			RenderingServer.viewport_get_measured_render_time_gpu(rid)],
		"%.2f M primitives   %d draw calls" % [
			viewport.get_render_info(Viewport.RENDER_INFO_TYPE_VISIBLE,
				Viewport.RENDER_INFO_PRIMITIVES_IN_FRAME) / 1.0e6,
			viewport.get_render_info(Viewport.RENDER_INFO_TYPE_VISIBLE,
				Viewport.RENDER_INFO_DRAW_CALLS_IN_FRAME)],
	]

	var grass := _grass()
	if grass != null:
		lines.append("grass %.2f M wanted / %.2f M budget   %.2f M on screen   exponent %.1f   fade %d m" % [
			grass.WantedTriangles / 1.0e6, grass.MaxVisibleTriangles / 1.0e6,
			grass.OnScreenTriangles / 1.0e6, grass.Exponent, grass.FadeMetres])

	return lines


## Switches the sun's PSSM cascade blending, which is where a shadow edge can wobble on its own.
##
## Each cascade snaps to its own texel grid as the camera moves, so inside a blend band two lookups at two
## texel sizes disagree about where an edge is. Turning the blend off should trade a sizzling edge for a
## visible hard seam that travels with the camera - and that trade is the tell.
func _set_blend_splits(on: bool) -> void:
	var sun := _sun()
	if sun == null:
		return

	sun.directional_shadow_blend_splits = on
	print("[render debug] shadow cascade blending: %s" % ("on" if on else "off"))
	_refresh()


## Stops the cloud shadow decals, [method Node.set_process] and all.
##
## Hiding the node is not enough by itself: [code]cloud_shadows.gd[/code] writes its own
## [member Node3D.visible] from the weather every frame, so it would be back the frame after. Taking its
## [code]_process[/code] away is what makes the switch hold, and giving it back lets the script work its own
## visibility out again.
func _set_cloud_shadows(on: bool) -> void:
	var clouds := _clouds()
	if clouds == null:
		return

	clouds.set_process(on)
	if not on:
		clouds.visible = false

	print("[render debug] cloud shadow decals: %s" % ("on" if on else "off"))
	_refresh()


## Writes the streamed chunks around the camera, and the camera, to [code]user://chunk_dumps[/code].
##
## For the water harness's [code]--chunks=[/code], which meshes the dump with the real renderer and looks at it
## from this same view, so a fault only the world's own ground shows can be worked on without a server. Two
## columns each way is five chunks across, which covers everything the camera sees at the spring arm's reach.
func _dump_chunks() -> void:
	var terrain := _find("terrain", func(node: Node) -> bool: return _has_script(node, _TERRAIN_SCRIPT))
	if terrain == null:
		push_warning("[render debug] no terrain in this scene; there is nothing to dump.")
		return

	var path: String = terrain.call("DumpChunks", get_viewport().get_camera_3d(), 2)
	if path.is_empty():
		push_warning("[render debug] the terrain holds no chunks yet; nothing was dumped.")
		return

	print("[render debug] chunks dumped to ", path)


## The environment the viewport is actually rendering with.
##
## The [WorldEnvironment] node is preferred over [member World3D.environment] so that what is switched is the
## resource a scene authored, which is where anyone reading this afterwards will look for it.
func _environment() -> Environment:
	var node := _find("environment", func(child: Node) -> bool: return child is WorldEnvironment)
	if node != null:
		return (node as WorldEnvironment).environment

	var world := get_viewport().find_world_3d()

	return world.environment if world != null else null


## The one directional light that casts a shadow.
##
## Which is the sun and cannot be anything else: [code]environment.gd[/code] switches the moon's shadow and
## the lightning flash's off in its [method Node._ready] and never turns either back on. Found by that
## rather than by the node name, because it is the property that matters here.
func _sun() -> DirectionalLight3D:
	var node := _find("sun", func(child: Node) -> bool:
		return child is DirectionalLight3D and (child as DirectionalLight3D).shadow_enabled)

	return node as DirectionalLight3D


func _has_script(node: Node, path: String) -> bool:
	var script := node.get_script() as Script

	return script != null and script.resource_path == path


## The first node in the current scene that [param matches], or null, remembered under [param key].
##
## Remembered because the open panel and the frame-time line ask for four of these four times a second, and a
## walk over a few thousand nodes that often would show up in the very frame times it is there to measure. A
## remembered node is dropped once it is freed or leaves the tree, and the lot when the current scene changes -
## the scene under this node is the menu before login and the game after it. A miss is not remembered: the
## grass and the clouds arrive with the world, after the panel may already have asked.
func _find(key: String, matches: Callable) -> Node:
	var root := get_tree().current_scene
	if root != _scene:
		_scene = root
		_found.clear()

	# Untyped, because a typed Node variable refuses a freed instance before is_instance_valid can look at it.
	var node = _found.get(key)
	if is_instance_valid(node) and (node as Node).is_inside_tree():
		return node

	node = null if root == null else _walk(root, matches)
	_found[key] = node

	return node


## Depth-first, and it stops at a [SubViewport] rather than descending into one.
##
## The portraits, the health bars and the cast bars each render a little scene of their own inside one,
## complete with a [DirectionalLight3D] - so a walk that went in could hand the cascade blending switch the light that lights a
## character portrait instead of the sun. Nothing inside a sub-viewport is part of the world this rig is
## about. None of those lights casts a shadow today, which is what keeps this a guard rather than a fix.
func _walk(node: Node, matches: Callable) -> Node:
	if matches.call(node):
		return node

	if node is SubViewport:
		return null

	for child in node.get_children():
		var found := _walk(child, matches)
		if found != null:
			return found

	return null


func _grass() -> Node:
	return _find("grass", func(node: Node) -> bool: return _has_script(node, _GRASS_SCRIPT))


func _clouds() -> Node3D:
	return _find("clouds", func(node: Node) -> bool: return _has_script(node, _CLOUD_SCRIPT)) as Node3D


## Brings the readout and the panel up to date with the scene.
##
## The readout is hidden whenever everything is as authored, so that it is not sitting over the HUD for a
## session that never touches the panel - and so that "is anything switched" is answerable at a glance rather
## than by reading five lines to check they all say the default. The panel's switches are set from the scene
## rather than remembered, and one whose target this scene does not have is greyed out.
func _refresh() -> void:
	var env := _environment()
	var grass := _grass()
	var sun := _sun()
	var clouds := _clouds()

	if menu.visible:
		view_mode.select(_draw_index())
		_show(sdfgi, env != null and env.sdfgi_enabled, env != null)
		_show(ssil, env != null and env.ssil_enabled, env != null)
		_show(ssao, env != null and env.ssao_enabled, env != null)
		_show(grass_budget, _saved_budget < 0, grass != null)
		_show(blend_splits, sun != null and sun.directional_shadow_blend_splits, sun != null)
		_show(cloud_shadows, clouds != null and clouds.is_processing(), clouds != null)
		_show(frame_times, _stats, true)

	var lines: Array[String] = []

	if _stats:
		lines.append_array(_stats_lines())

	if _draw_index() != 0:
		lines.append("view mode: %s" % _DRAW_NAMES[_draw_index()])

	if env != null and not (env.sdfgi_enabled and env.ssil_enabled and env.ssao_enabled):
		lines.append("sdfgi %s   ssil %s   ssao %s" % [
			_state(env.sdfgi_enabled), _state(env.ssil_enabled), _state(env.ssao_enabled)])

	if _saved_budget >= 0:
		lines.append("grass draw budget off (was %d)" % _saved_budget)

	if sun != null and not sun.directional_shadow_blend_splits:
		lines.append("shadow cascade blending off")

	if clouds != null and not clouds.is_processing():
		lines.append("cloud shadow decals off")

	# Reported rather than switchable, because environment.gd rewrites it from the weather every ten seconds
	# and would undo any switch put on it. It earns its place all the same: it is 0 under a clear sky and
	# opens up to 3 degrees under a closed deck - see WeatherLook.SunAngularDegreesFor - and anything above 0
	# puts Godot on the blocker-search soft shadow filter, whose sample disc is randomised in screen space
	# and therefore crawls when the camera moves. A flicker that only happens under cloud is that filter.
	if sun != null and sun.light_angular_distance > 0.0 and not lines.is_empty():
		lines.append("sun angular size %.2f deg (soft shadow filter active)" % sun.light_angular_distance)

	readout_label.text = "\n".join(lines)
	readout.visible = not lines.is_empty()


## Where the viewport's debug draw sits in [constant _DRAW_MODES]; 0 for one set from outside the list.
func _draw_index() -> int:
	return maxi(_DRAW_MODES.find(get_viewport().debug_draw), 0)


## Sets a switch without firing it, so that showing the scene's state does not write it back.
func _show(button: BaseButton, pressed: bool, available: bool) -> void:
	button.set_pressed_no_signal(pressed)
	button.disabled = not available


func _state(enabled: bool) -> String:
	return "on" if enabled else "OFF"
