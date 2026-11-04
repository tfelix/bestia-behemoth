extends Node

## Turns a conversation from the server into a dialog on screen, and a clicked option into an answer.
##
## Everything visible belongs to [MessageDialog]; this only decides *what* to show and what a choice
## means. That split is the reason a conversation needed no second window: a conversation step is a
## [DialogContent] whose options are non-empty, and the queue, the presenter slot and the text
## resolution are the ones dialogs have always used.
##
## Replaced the chat-window stub this shipped behind. Nothing about the server changed for it - an
## option id is a topic id, the server keeps no conversation state, and a stale click resolves to
## whatever it would have resolved to anyway.
##
## A node in [code]UI.tscn[/code]: the server only ever sends a conversation in answer to the player talking to
## someone, so there is always a world, and [code]dialog_manager.gd[/code] buffers the rest.

## Who the current conversation is with, so a chosen option can be sent back to them.
var _speaker_entity_id: int = 0


func _ready() -> void:
	ConnectionManager.conversation_received.connect(_on_conversation_received)


func _on_conversation_received(message) -> void:
	_speaker_entity_id = message.SpeakerEntityId

	DialogManager.show_content(DialogContent.of_conversation(message, _answer))


## Sends the player's choice. Bound into each button by [MessageDialog], which knows the topic id but
## not who is being talked to.
func _answer(topic_id: int) -> void:
	if _speaker_entity_id == 0:
		return

	ConnectionManager.answer_conversation(_speaker_entity_id, topic_id)
