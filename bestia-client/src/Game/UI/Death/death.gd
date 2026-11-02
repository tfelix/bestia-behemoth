extends Control
## "You have died" overlay, and the way back from it.
##
## Driven entirely by the server's [code]DeadComponentSMSG[/code] for the player's own master: it
## arriving opens the window, and it arriving again with Removed = true - the component being taken
## off on respawn - closes it. Nothing here decides that the player is dead.
##
## Respawning is optional on purpose. The window can be dismissed and the body left lying there for
## as long as the player likes; [Options] grows a Respawn button while dead so the choice is always
## reachable again. Leaving the world dead is no escape either - the server resolves the respawn on
## the way out, so the player returns at their save point with 1 HP regardless.

## Emitted whenever the local player's dead state changes, so the ESC menu can offer its own
## Respawn button for as long as it applies.
signal dead_state_changed(is_dead: bool)

const _FADE_SECONDS := 0.35

@onready var _panel: Control = $Panel
@onready var _respawn_button: Button = $Panel/CenterContainer/PanelContainer/VBoxContainer/RespawnButton

var _is_dead: bool = false
var _fade: Tween


func _ready() -> void:
	# The scrim rather than us, for the reason the options menu documents: our root spans the screen
	# whether the player is dead or not.
	_panel.add_to_group("world_blocking_ui")
	_panel.visible = false
	_style_panel()

	_respawn_button.pressed.connect(_on_respawn_pressed)
	$Panel/CenterContainer/PanelContainer/VBoxContainer/DismissButton.pressed.connect(_on_dismiss_pressed)

	ConnectionManager.entity_received.connect(_on_entity_received)


## Dresses the card like the ESC menu and the settings panel; the scene only places the nodes.
func _style_panel() -> void:
	# A red cast on the scrim, so the window reads as death before a word of it is read.
	$Panel/Scrim.color = Color(0.12, 0.01, 0.02, 0.7)

	var card: PanelContainer = $Panel/CenterContainer/PanelContainer
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.09, 0.09, 0.1, 0.94)
	style.border_color = Color(0.85, 0.7, 0.35, 0.55)
	style.set_border_width_all(1)
	style.set_corner_radius_all(8)
	style.shadow_color = Color(0, 0, 0, 0.5)
	style.shadow_size = 18
	style.set_content_margin_all(32)
	card.add_theme_stylebox_override("panel", style)

	var column: VBoxContainer = $Panel/CenterContainer/PanelContainer/VBoxContainer
	column.custom_minimum_size = Vector2(360, 0)
	column.add_theme_constant_override("separation", 18)

	var title: Label = $Panel/CenterContainer/PanelContainer/VBoxContainer/TitleLabel
	title.add_theme_font_size_override("font_size", 28)
	title.add_theme_color_override("font_color", UiStyle.DANGER)

	$Panel/CenterContainer/PanelContainer/VBoxContainer/TextLabel.add_theme_color_override("font_color", UiStyle.MUTED)

	for child in column.get_children():
		if child is Button:
			child.custom_minimum_size = Vector2(0, 40)
	# The way out of this window, so it stays gold whether hovered, focused or neither.
	for state in ["font_color", "font_hover_color", "font_focus_color", "font_pressed_color"]:
		_respawn_button.add_theme_color_override(state, UiStyle.ACCENT)


## Shows the window, fading it in rather than snapping the screen dark.
func _open() -> void:
	if _panel.visible:
		return
	if _fade != null:
		_fade.kill()
	_panel.modulate.a = 0.0
	_panel.visible = true
	_fade = create_tween()
	_fade.tween_property(_panel, "modulate:a", 1.0, _FADE_SECONDS)
	_respawn_button.grab_focus()


func _on_entity_received(message) -> void:
	if not (message is DeadComponentSMSG):
		return

	# Everyone in range hears about every player body, including other people's. Matched against the
	# id rather than the Entity node for the same reason options.gd does: the id survives whether or
	# not the node is currently in the table.
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null or message.EntityId != entity_manager.get_owned_master_entity_id():
		return

	_is_dead = not message.Removed
	if _is_dead:
		_open()
	else:
		_panel.visible = false
	dead_state_changed.emit(_is_dead)


func _on_respawn_pressed() -> void:
	# The window stays up until the server confirms by removing the component, so a lost or refused
	# request cannot leave the player looking alive while their body is still on the ground.
	ConnectionManager.request_respawn()


func _on_dismiss_pressed() -> void:
	_panel.visible = false


## Whether the local player is currently a body waiting to respawn.
func is_dead() -> bool:
	return _is_dead


## Reopens the window after it was dismissed - what the ESC menu's Respawn button calls.
func reopen() -> void:
	if _is_dead:
		_open()
