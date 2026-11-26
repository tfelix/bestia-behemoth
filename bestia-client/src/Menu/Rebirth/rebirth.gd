extends Control
## Waits out the birth of a new world: says why the game cannot start, and tries again by itself.
##
## A stored session is resumed every few seconds. The login server refuses it while the world is being born, which
## keeps the player here; once it lets the session through, the game goes on to the master select as usual. Without
## a stored session there is nothing to try, so the player signs in again from the title.

const _RETRY_SECONDS := 20.0

@onready var _status: Label = %StatusLabel
@onready var _retry: Timer = $RetryTimer


func _ready() -> void:
	%BackButton.pressed.connect(_on_back_pressed)
	_retry.timeout.connect(_on_retry_timeout)
	ConnectionManager.world_rebirth_underway.connect(_wait_again)
	ConnectionManager.passkey_login_failed.connect(_on_login_failed)
	ConnectionManager.session_resume_unavailable.connect(_on_session_gone)

	if ConnectionManager.has_stored_session():
		_wait_again()
	else:
		_on_session_gone()


func _on_retry_timeout() -> void:
	_status.text = tr("REBIRTH_TRYING")
	ConnectionManager.resume_session()


func _wait_again() -> void:
	_status.text = tr("REBIRTH_WAITING")
	_retry.start(_RETRY_SECONDS)


## The login server could not be reached at all. That is no reason to stop waiting.
func _on_login_failed(reason: String) -> void:
	_status.text = reason
	_retry.start(_RETRY_SECONDS)


func _on_session_gone() -> void:
	_retry.stop()
	_status.text = tr("REBIRTH_SIGN_IN")


func _on_back_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")
