extends Node

## Runtime switches for the things that can make a shadow appear to flicker while the camera moves, so that
## each one can be taken out of the picture without a restart, a scene edit or a rebuild.
##
## [b]Why this is a tool and not a fix.[/b] A shadow that flickers only while you move has several candidate
## causes in this client and they live in different files: the environment's SDFGI and its two screen-space
## passes, the sun's PSSM cascade blending, the grass field's per-frame level of detail, and the cloud shadow
## decals. Every one of them is still when the camera is still, which is what makes reading the code
## insufficient - the question is which one stops when you switch it off, and that is a question about a
## running game. So the keys below are an A/B rig rather than a settings menu, and the readout exists so that
## a screenshot of a flicker records which of them were on when it was taken.
##
## [b]An autoload, so that it works wherever there is a 3D world[/b] - the game scene and
## [code]Dev/TerrainTestbed[/code] alike - and so that nothing in a gameplay scene has to know it exists. It
## resolves the nodes it touches by walking the current scene on each keypress rather than caching them at
## startup, because the scene it is looking at changes when the player logs in.
##
## [b]Inert outside a debug build.[/b] The whole node switches itself off under
## [method OS.is_debug_build], so an exported release carries a few hundred bytes of script and no input
## handler, no overlay and no per-frame cost.

## The script that draws the decorative grass field, found by path rather than by class name.
##
## [code]TerrainGrass[/code] is a C# [code]GlobalClass[/code], so [code]is[/code] would very likely work -
## but [code]game.gd[/code] reaches the same class through [method @GDScript.preload] rather than by its
## global name, and a debug tool is a poor place to be the first to depend on that registration.
const _GRASS_SCRIPT := "res://Game/World/TerrainGrass.cs"

const _CLOUD_SCRIPT := "res://Game/Environment/CloudShadows/cloud_shadows.gd"

## The view modes F1 walks through, in the order they answer questions.
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

var _on := false

var _draw := 0

## What F9 took away, so that restoring puts back what the scene authored rather than a guess at it. Empty
## whenever the three are as the environment left them.
var _saved_gi := {}

## The grass budget F10 took away, or -1 while the field is running on its own.
var _saved_budget := -1

var _layer: CanvasLayer = null
var _panel: PanelContainer = null
var _label: Label = null


func _ready() -> void:
	# Nothing here should survive into a release export - not the input handler, and not an overlay that
	# could be brought up in front of a player.
	_on = OS.is_debug_build()
	if not _on:
		set_process_input(false)
		return

	# Printed rather than only shown, because the readout is hidden until something is off its default and a
	# key nobody knows about is a key nobody presses.
	print("[render debug] F1 / shift+F1 cycle view mode, F2 sdfgi, F3 ssil, F4 ssao, ",
		"F9 all three off, F10 grass draw budget, F11 shadow cascade blending, F12 cloud shadow decals")

	_build_readout()


## Only the keys below are claimed, and each of them is marked handled.
##
## [method Node._input] rather than [method Node._unhandled_input], which is the one deliberate rudeness
## here: [code]_unhandled_input[/code] runs after the UI has had its turn, so a focused chat box would eat
## every one of these. Debugging a flicker while the chat happens to be open is exactly when that would be
## most annoying to discover.
func _input(event: InputEvent) -> void:
	if not _on:
		return

	var key := event as InputEventKey
	if key == null or not key.pressed or key.echo:
		return

	# Physical, so the rig works on a keyboard layout this repo has not been opened on.
	match key.physical_keycode:
		KEY_F1:
			_cycle_draw(-1 if key.shift_pressed else 1)
		KEY_F2:
			_toggle_gi("sdfgi_enabled", "sdfgi")
		KEY_F3:
			_toggle_gi("ssil_enabled", "ssil")
		KEY_F4:
			_toggle_gi("ssao_enabled", "ssao")
		KEY_F9:
			_toggle_all_gi()
		KEY_F10:
			_toggle_grass_budget()
		KEY_F11:
			_toggle_blend_splits()
		KEY_F12:
			_toggle_cloud_shadows()
		_:
			return

	get_viewport().set_input_as_handled()
	_refresh()


func _cycle_draw(step: int) -> void:
	_draw = wrapi(_draw + step, 0, _DRAW_MODES.size())
	get_viewport().debug_draw = _DRAW_MODES[_draw]
	print("[render debug] view mode: %s" % _DRAW_NAMES[_draw])


func _toggle_gi(property: StringName, label: String) -> void:
	var env := _environment()
	if env == null:
		push_warning("[render debug] no Environment in this scene; %s cannot be switched." % label)
		return

	# Flipping one of the three by hand leaves F9 with a stale idea of what it took away, so it gives up its
	# saved state rather than restoring the wrong thing later.
	_saved_gi.clear()

	var value: bool = not bool(env.get(property))
	env.set(property, value)
	print("[render debug] %s: %s" % [label, "on" if value else "off"])


## Switches SDFGI and both screen-space passes off together, and back on to what the scene authored.
##
## The one-key form of the whole first hypothesis. All three are only visible where ambient light is the
## dominant term, which in a sunlit world means inside the shadows - so if a flicker in the shadows survives
## this, it is not any of them and the search moves to the shadow map itself.
func _toggle_all_gi() -> void:
	var env := _environment()
	if env == null:
		push_warning("[render debug] no Environment in this scene; the GI passes cannot be switched.")
		return

	if _saved_gi.is_empty():
		_saved_gi = {
			"sdfgi_enabled": env.sdfgi_enabled,
			"ssil_enabled": env.ssil_enabled,
			"ssao_enabled": env.ssao_enabled,
		}
		env.sdfgi_enabled = false
		env.ssil_enabled = false
		env.ssao_enabled = false
		print("[render debug] sdfgi, ssil and ssao: off")
		return

	for property in _saved_gi:
		env.set(property, _saved_gi[property])

	_saved_gi.clear()
	print("[render debug] sdfgi, ssil and ssao: restored")


## Takes the grass field's draw budget away, which takes its whole level-of-detail controller with it.
##
## [code]MaxVisibleTriangles[/code] of 0 means no budget, and that is a bigger switch than it sounds: with no
## budget [code]GrassLod.BudgetTrim[/code] returns 1 and [code]GrassLod.NextExponent[/code] returns 1, so
## the per-frame feedback loop that thins the far field is out of the picture, and so is the
## [code]grass_field_falloff[/code] global it publishes to the terrain shader every frame. If the ground
## stops breathing with this off, the flicker was the field being retuned and not a shadow.
##
## It costs frames, and is meant to: every tuft the field wanted is now drawn.
func _toggle_grass_budget() -> void:
	var grass := _find(func(node: Node) -> bool: return _has_script(node, _GRASS_SCRIPT))
	if grass == null:
		push_warning("[render debug] no TerrainGrass in this scene; the grass budget cannot be switched.")
		return

	if _saved_budget < 0:
		_saved_budget = grass.MaxVisibleTriangles
		grass.MaxVisibleTriangles = 0
		print("[render debug] grass draw budget: off (was %d)" % _saved_budget)
		return

	grass.MaxVisibleTriangles = _saved_budget
	print("[render debug] grass draw budget: %d" % _saved_budget)
	_saved_budget = -1


## Switches the sun's PSSM cascade blending, which is where a shadow edge can wobble on its own.
##
## Each cascade snaps to its own texel grid as the camera moves, so inside a blend band two lookups at two
## texel sizes disagree about where an edge is. Turning the blend off should trade a sizzling edge for a
## visible hard seam that travels with the camera - and that trade is the tell.
func _toggle_blend_splits() -> void:
	var sun := _sun()
	if sun == null:
		push_warning("[render debug] no shadow-casting DirectionalLight3D; the splits cannot be switched.")
		return

	sun.directional_shadow_blend_splits = not sun.directional_shadow_blend_splits
	print("[render debug] shadow cascade blending: %s"
		% ("on" if sun.directional_shadow_blend_splits else "off"))


## Stops the cloud shadow decals, [method Node.set_process] and all.
##
## Hiding the node is not enough by itself: [code]cloud_shadows.gd[/code] writes its own
## [member Node3D.visible] from the weather every frame, so it would be back the frame after. Taking its
## [code]_process[/code] away is what makes the switch hold, and giving it back lets the script work its own
## visibility out again.
func _toggle_cloud_shadows() -> void:
	var clouds := _find(func(node: Node) -> bool: return _has_script(node, _CLOUD_SCRIPT))
	if clouds == null:
		push_warning("[render debug] no CloudShadows in this scene; the decals cannot be switched.")
		return

	var running := clouds.is_processing()
	clouds.set_process(not running)
	if running:
		clouds.visible = false

	print("[render debug] cloud shadow decals: %s" % ("off" if running else "on"))


## The environment the viewport is actually rendering with.
##
## The [WorldEnvironment] node is preferred over [member World3D.environment] so that what is switched is the
## resource a scene authored, which is where anyone reading this afterwards will look for it.
func _environment() -> Environment:
	var node := _find(func(child: Node) -> bool: return child is WorldEnvironment)
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
	var node := _find(func(child: Node) -> bool:
		return child is DirectionalLight3D and (child as DirectionalLight3D).shadow_enabled)

	return node as DirectionalLight3D


func _has_script(node: Node, path: String) -> bool:
	var script := node.get_script() as Script

	return script != null and script.resource_path == path


## The first node in the current scene that [param matches], or null.
##
## Walked on demand rather than cached, because the scene under this autoload is the menu before login and
## the game after it. A depth-first walk over a few thousand nodes, once per keypress, is not worth being
## clever about.
func _find(matches: Callable) -> Node:
	var root := get_tree().current_scene

	return null if root == null else _walk(root, matches)


## Depth-first, and it stops at a [SubViewport] rather than descending into one.
##
## The portraits, the health bars and the cast bars each render a little scene of their own inside one,
## complete with a [DirectionalLight3D] - so a walk that went in could hand F11 the light that lights a
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


func _build_readout() -> void:
	_layer = CanvasLayer.new()
	_layer.layer = 128
	add_child(_layer)

	_panel = PanelContainer.new()
	_panel.offset_left = 8.0
	_panel.offset_top = 8.0
	_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE

	var box := StyleBoxFlat.new()
	box.bg_color = Color(0.0, 0.0, 0.0, 0.55)
	box.content_margin_left = 8.0
	box.content_margin_right = 8.0
	box.content_margin_top = 4.0
	box.content_margin_bottom = 4.0
	_panel.add_theme_stylebox_override("panel", box)

	_label = Label.new()
	_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_panel.add_child(_label)

	_layer.add_child(_panel)
	_refresh()


## Redraws the readout, and hides it whenever everything is as authored.
##
## Hidden rather than merely quiet, so that the overlay is not sitting over the HUD for a session that never
## touches these keys - and so that "is anything switched" is answerable at a glance rather than by reading
## five lines to check they all say the default.
func _refresh() -> void:
	if _label == null:
		return

	var lines: Array[String] = []

	if _draw != 0:
		lines.append("view mode: %s" % _DRAW_NAMES[_draw])

	var env := _environment()
	if env != null and not (env.sdfgi_enabled and env.ssil_enabled and env.ssao_enabled):
		lines.append("sdfgi %s   ssil %s   ssao %s" % [
			_state(env.sdfgi_enabled), _state(env.ssil_enabled), _state(env.ssao_enabled)])

	if _saved_budget >= 0:
		lines.append("grass draw budget off (was %d)" % _saved_budget)

	var sun := _sun()
	if sun != null and not sun.directional_shadow_blend_splits:
		lines.append("shadow cascade blending off")

	var clouds := _find(func(node: Node) -> bool: return _has_script(node, _CLOUD_SCRIPT))
	if clouds != null and not clouds.is_processing():
		lines.append("cloud shadow decals off")

	# Reported rather than switchable, because environment.gd rewrites it from the weather every ten seconds
	# and would undo any switch put on it. It earns its place all the same: it is 0 under a clear sky and
	# opens up to 3 degrees under a closed deck - see WeatherLook.SunAngularDegreesFor - and anything above 0
	# puts Godot on the blocker-search soft shadow filter, whose sample disc is randomised in screen space
	# and therefore crawls when the camera moves. A flicker that only happens under cloud is that filter.
	if sun != null and sun.light_angular_distance > 0.0 and not lines.is_empty():
		lines.append("sun angular size %.2f deg (soft shadow filter active)" % sun.light_angular_distance)

	_label.text = "\n".join(lines)
	_layer.visible = not lines.is_empty()


func _state(enabled: bool) -> String:
	return "on" if enabled else "OFF"
