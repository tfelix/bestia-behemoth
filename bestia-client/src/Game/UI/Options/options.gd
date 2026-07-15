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

var _pending_action: PendingAction = PendingAction.NONE
# Local mirror of the server countdown, ticked down each frame for a smooth display and corrected
# whenever a fresh sync arrives. Negative when no logout is running.
var _remaining_seconds: float = -1.0


func _ready() -> void:
	_menu.visible = false
	_countdown.visible = false

	$Menu/CenterContainer/PanelContainer/VBoxContainer/ContinueButton.pressed.connect(_hide_menu)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/MasterSelectButton.pressed.connect(_on_master_select_pressed)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/DisconnectButton.pressed.connect(_on_disconnect_pressed)
	$Menu/CenterContainer/PanelContainer/VBoxContainer/ExitGameButton.pressed.connect(_on_exit_pressed)
	$Countdown/CenterContainer/PanelContainer/VBoxContainer/CancelLogoutButton.pressed.connect(_on_cancel_logout_pressed)
	_confirm_exit.confirmed.connect(_on_exit_confirmed)

	ConnectionManager.logout_countdown_received.connect(_on_logout_countdown)
	ConnectionManager.logout_cancelled.connect(_on_logout_cancelled)
	ConnectionManager.entity_received.connect(_on_entity_received)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		_toggle_menu()
		get_viewport().set_input_as_handled()


func _process(delta: float) -> void:
	if _remaining_seconds < 0.0:
		return
	_remaining_seconds = max(0.0, _remaining_seconds - delta)
	_countdown_label.text = "Logging out in %d s" % int(ceil(_remaining_seconds))


func _toggle_menu() -> void:
	if _menu.visible:
		_hide_menu()
	else:
		_menu.visible = true


func _hide_menu() -> void:
	_menu.visible = false


# --- Menu button handlers: pick an action, then start the protected logout. ---

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
