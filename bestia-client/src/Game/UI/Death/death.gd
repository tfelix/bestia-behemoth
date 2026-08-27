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

@onready var _panel: Control = $Panel

var _is_dead: bool = false


func _ready() -> void:
	_panel.visible = false

	$Panel/CenterContainer/PanelContainer/VBoxContainer/RespawnButton.pressed.connect(_on_respawn_pressed)
	$Panel/CenterContainer/PanelContainer/VBoxContainer/DismissButton.pressed.connect(_on_dismiss_pressed)

	ConnectionManager.entity_received.connect(_on_entity_received)


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
	_panel.visible = _is_dead
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
		_panel.visible = true
