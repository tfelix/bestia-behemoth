extends Control

@onready var _version_label = %VersionLabel
@onready var _login_button = %LoginButton
@onready var _status_label = %StatusLabel
@onready var _cancel_login_button = %CancelLoginButton
@onready var _sign_out_button = %SignOutButton


func _ready() -> void:
	ConnectionManager.disconnect_from_server()
	_version_label.text = SettingsManager.version
	_reset_buttons()

	ConnectionManager.passkey_browser_opened.connect(_on_passkey_browser_opened)
	ConnectionManager.passkey_login_failed.connect(_on_passkey_login_failed)
	ConnectionManager.session_resume_unavailable.connect(_on_session_resume_unavailable)


func _on_quit_button_pressed() -> void:
	get_tree().root.propagate_notification(NOTIFICATION_WM_CLOSE_REQUEST)
	get_tree().quit()


func _on_settings_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Settings/Settings.tscn")


func _on_credits_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Credits/Credits.tscn")


## One button for both signing in and signing up: the page that opens in the browser offers a
## passkey, account creation and recovery side by side, so the choice is made there rather than here.
##
## A stored session skips all of that, which is the ordinary case after the first run.
func _on_login_button_pressed() -> void:
	_set_buttons_disabled(true)

	if ConnectionManager.has_stored_session():
		_show_status("Signing you in...")
		ConnectionManager.resume_session()
	else:
		_show_status("Opening your browser...")
		ConnectionManager.login_with_passkey()


## Wanted on a shared machine: the stored session is what lets anyone who sits down here start the game
## as this account, so there has to be a way to take it away.
func _on_sign_out_button_pressed() -> void:
	ConnectionManager.sign_out()
	_reset_buttons()
	_show_status("Signed out on this computer.")


func _on_cancel_login_button_pressed() -> void:
	ConnectionManager.cancel_passkey_login()


## The browser is a separate window that may well be behind the game, so say where the player has to
## look. Nothing else in the menu can report on a ceremony happening outside the process.
func _on_passkey_browser_opened() -> void:
	_show_status("Finish signing in with your passkey in the browser window.")
	_cancel_login_button.visible = true


func _on_passkey_login_failed(reason: String) -> void:
	_reset_buttons()
	_show_status(reason)


## The stored session is gone. The player asked to sign in and still wants to get in, so carry straight
## on into the browser rather than making them press the same button twice.
func _on_session_resume_unavailable() -> void:
	_show_status("Opening your browser...")
	ConnectionManager.login_with_passkey()


func _show_status(text: String) -> void:
	_status_label.text = text
	_status_label.visible = true


func _reset_buttons() -> void:
	_set_buttons_disabled(false)
	_cancel_login_button.visible = false
	_sign_out_button.visible = ConnectionManager.has_stored_session()
	_status_label.visible = false


func _set_buttons_disabled(disabled: bool) -> void:
	_login_button.disabled = disabled
	_sign_out_button.disabled = disabled
