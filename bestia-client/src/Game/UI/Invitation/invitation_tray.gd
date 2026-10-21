class_name InvitationTray
extends VBoxContainer

## Where invitations wait for an answer: near the top of the screen, out of the way of the fight.
##
## Not a dialog, which would be modal and take the keyboard away mid-fight; a party invitation and a guild one
## can stand here at once. One per kind: a newer invitation of the same kind replaces the older one, whose
## prompt the player had not answered yet.

## Seconds a prompt stays when the server did not say. Mirrors the server's default invitation timeout.
const _DEFAULT_LIFETIME_SECONDS := 60.0

var _prompts: Dictionary[StringName, InvitationPrompt] = {}


func _ready() -> void:
	add_to_group("world_blocking_ui")
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_theme_constant_override("separation", 8)


## Asks [param text]; [param on_answer] is called once with the answer, or never if it runs out.
func offer(kind: StringName, text: String, lifetime_seconds: float, on_answer: Callable) -> void:
	var previous: InvitationPrompt = _prompts.get(kind)
	if is_instance_valid(previous):
		previous.queue_free()

	var prompt := InvitationPrompt.new()
	prompt.setup(text, lifetime_seconds if lifetime_seconds > 0.0 else _DEFAULT_LIFETIME_SECONDS)
	prompt.answered.connect(on_answer)
	add_child(prompt)
	_prompts[kind] = prompt


## Takes the prompt of [param kind] away unanswered, when what it asked about is over.
func withdraw(kind: StringName) -> void:
	var prompt: InvitationPrompt = _prompts.get(kind)
	if is_instance_valid(prompt):
		prompt.queue_free()
	_prompts.erase(kind)
