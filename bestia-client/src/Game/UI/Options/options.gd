extends Control
## ESC menu overlay + logout flow.
##
## Toggled with ESC (or the Continue button); the game keeps running behind a dimmed scrim. The
## three exit paths (Master Select / Logout to menu / Exit game) all go through the server's delayed,
## cancellable logout: we request it, show a countdown, and only perform the chosen action once the
## server confirms the master has despawned (its vanish). Moving, using a skill or taking damage
## aborts the logout server-side; the Cancel button does the same by sending an empty-path move.

enum PendingAction { NONE, MASTER_SELECT, DISCONNECT, EXIT }

const MASTER_SELECT_SCENE := "res://Menu/MasterSelect/MasterSelect.tscn"

@onready var _menu: Control = $Menu
@onready var _countdown: Control = $Countdown
@onready var _countdown_label: Label = $Countdown/CenterContainer/PanelContainer/VBoxContainer/CountdownLabel
@onready var _confirm_exit: ConfirmationDialog = $ConfirmExitDialog
@onready var _main_panel: Control = $Menu/CenterContainer
@onready var _settings_holder: Control = $Menu/SettingsHolder

var _pending_action: PendingAction = PendingAction.NONE
var _settings_panel: SettingsPanel
# Local mirror of the server countdown, ticked down each frame for a smooth display and corrected
# whenever a fresh sync arrives. Negative when no logout is running.
var _remaining_seconds: float = -1.0


func _ready() -> void:
	# The scrim rather than us: our own root spans the screen whether the menu is open or not, and a
	# permanent screen-sized claim would leave the world unpointable.
	_menu.add_to_group("world_blocking_ui")
	_menu.visible = false
	_countdown.visible = false
	_style_menu()

	_settings_holder.add_child(_build_settings_panel())
	$Menu/CenterContainer/PanelContainer/VBoxContainer/SettingsButton.pressed.connect(_show_settings)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/ContinueButton.pressed.connect(_hide_menu)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/RespawnButton.pressed.connect(_on_respawn_pressed)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/MasterSelectButton.pressed.connect(_on_master_select_pressed)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/DisconnectButton.pressed.connect(_on_disconnect_pressed)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/ExitGameButton.pressed.connect(_on_exit_pressed)
	$Countdown/CenterContainer/PanelContainer/VBoxContainer/CancelLogoutButton.pressed.connect(_on_cancel_logout_pressed)
	_confirm_exit.confirmed.connect(_on_exit_confirmed)

	ConnectionManager.logout_countdown_received.connect(_on_logout_countdown)
	ConnectionManager.logout_cancelled.connect(_on_logout_cancelled)
	ConnectionManager.entity_received.connect(_on_entity_received)

	# The death window owns the dead state; this button is only the way back to it after the player
	# dismissed it, so it is hidden whenever there is nothing to respawn from.
	var death := get_parent().get_node_or_null("Death")
	if death != null:
		death.dead_state_changed.connect(_on_dead_state_changed)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		toggle_menu()
		get_viewport().set_input_as_handled()


func _process(delta: float) -> void:
	if _remaining_seconds < 0.0:
		return
	_remaining_seconds = max(0.0, _remaining_seconds - delta)
	_countdown_label.text = "Logging out in %d s" % int(ceil(_remaining_seconds))


func toggle_menu() -> void:
	if _settings_holder.visible:
		# ESC steps back out of the settings rather than closing the whole menu.
		_hide_settings()
	elif _menu.visible:
		_hide_menu()
	else:
		_show_menu()


func _show_menu() -> void:
	_menu.visible = true
	_hide_settings()
	$Menu/CenterContainer/PanelContainer/VBoxContainer/ContinueButton.grab_focus()


func _hide_menu() -> void:
	_menu.visible = false
	_hide_settings()


func _show_settings() -> void:
	_main_panel.visible = false
	_settings_holder.visible = true
	_settings_panel.focus_back()


func _hide_settings() -> void:
	_settings_holder.visible = false
	_main_panel.visible = true
	SettingsManager.save()


func _build_settings_panel() -> SettingsPanel:
	_settings_panel = SettingsPanel.new()
	_settings_panel.closed.connect(_hide_settings)
	return _settings_panel


## Dresses the panel; the scene only places the nodes.
func _style_menu() -> void:
	$Menu/Scrim.color = Color(0.02, 0.02, 0.04, 0.72)
	var panel: PanelContainer = $Menu/CenterContainer/PanelContainer
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.09, 0.09, 0.1, 0.94)
	style.border_color = Color(0.85, 0.7, 0.35, 0.55)
	style.set_border_width_all(1)
	style.set_corner_radius_all(8)
	style.shadow_color = Color(0, 0, 0, 0.5)
	style.shadow_size = 18
	style.set_content_margin_all(32)
	panel.add_theme_stylebox_override("panel", style)

	var column: VBoxContainer = $Menu/CenterContainer/PanelContainer/VBoxContainer
	column.custom_minimum_size = Vector2(280, 0)
	for child in column.get_children():
		if child is Button:
			child.custom_minimum_size = Vector2(0, 40)

	$Menu/CenterContainer/PanelContainer/VBoxContainer/ExitGameButton.add_theme_color_override("font_color", Color(0.95, 0.5, 0.45))


# --- Menu button handlers: pick an action, then start the protected logout. ---

func _on_respawn_pressed() -> void:
	_hide_menu()
	var death := get_parent().get_node_or_null("Death")
	if death != null:
		death.reopen()


func _on_dead_state_changed(is_dead: bool) -> void:
	$Menu/CenterContainer/PanelContainer/VBoxContainer/RespawnButton.visible = is_dead


func _on_master_select_pressed() -> void:
	_begin_logout(PendingAction.MASTER_SELECT)


func _on_disconnect_pressed() -> void:
	_begin_logout(PendingAction.DISCONNECT)


func _on_exit_pressed() -> void:
	_confirm_exit.popup_centered()


func _on_exit_confirmed() -> void:
	_begin_logout(PendingAction.EXIT)


func _begin_logout(action: PendingAction) -> void:
	_pending_action = action
	_hide_menu()
	_countdown.visible = true
	_remaining_seconds = -1.0
	_countdown_label.text = "Logging out..."
	ConnectionManager.request_logout()


func _on_cancel_logout_pressed() -> void:
	# Server confirms the cancel via logout_cancelled (which resets the UI); reused empty-path move.
	ConnectionManager.cancel_logout()


# --- Server-driven logout state ---

func _on_logout_countdown(remaining_seconds: float) -> void:
	# A countdown arriving means a logout is active; adopt the authoritative remaining time.
	_countdown.visible = true
	_remaining_seconds = remaining_seconds


func _on_logout_cancelled() -> void:
	_pending_action = PendingAction.NONE
	_remaining_seconds = -1.0
	_countdown.visible = false


func _on_entity_received(message) -> void:
	# Our own master vanishing is the server's "logout complete" signal -> run the queued action.
	if _pending_action == PendingAction.NONE:
		return
	if not (message is VanishEntitySMSG):
		return

	var entity_manager := EntityManager.get_instance()
	if entity_manager == null or message.EntityId != entity_manager.get_owned_master_entity_id():
		return

	# Only a real removal counts. An out-of-sight vanish says the entity is still alive and merely outside
	# the view, which for our own master means a terrain hiccup - not that the logout went through.
	if message.IsOutOfSight():
		return

	_execute_pending_action()


func _execute_pending_action() -> void:
	var action := _pending_action
	_pending_action = PendingAction.NONE
	_remaining_seconds = -1.0
	_countdown.visible = false

	match action:
		PendingAction.MASTER_SELECT:
			SceneManager.goto_scene(MASTER_SELECT_SCENE)
		PendingAction.DISCONNECT:
			ConnectionManager.disconnect_to_main_menu()
		PendingAction.EXIT:
			get_tree().quit()
