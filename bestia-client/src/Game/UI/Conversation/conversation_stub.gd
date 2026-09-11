extends Node
class_name ConversationStub

## Shows a conversation in the chat window, until there is a panel for one.
##
## Deliberately scaffolding, and deliberately shipped rather than waited for: the server half is
## complete and a feature that cannot be reached is one nobody finds out is broken. Everything real is
## already here - the key resolution, the typed arguments, the topic ids - so what a proper panel has to
## add is layout and buttons, not behaviour.
##
## [b]Replacing this[/b]: the panel wants [code]MessageDialog[/code] with a [code]VBoxContainer[/code] of
## buttons under its body, and [code]DialogManager[/code]'s single presenter slot is the reason to grow
## that node rather than add a second scene. Nothing here needs porting - the text already resolves
## through [DialogText], and a button only has to carry the option's [code]TopicId[/code] into
## [method ConnectionManager.answer_conversation].

## Which entity the last conversation was with, so [code]/pick[/code] can answer it.
var _speaker_entity_id: int = 0

## Topic ids of the options last offered, by their printed number.
var _topics: Array[int] = []

var _chat: Node = null


func _ready() -> void:
	ConnectionManager.conversation_received.connect(_on_conversation_received)


## The chat window to print into. Set by whoever owns both.
func bind_chat(chat: Node) -> void:
	_chat = chat


## Answers the nth option that was printed, counting from one.
##
## The real client sends the option's topic id straight off the button. A number is what a person can
## type, so the mapping lives here rather than making anybody read a six-figure id off a chat line.
func pick(option_number: int) -> void:
	if _speaker_entity_id == 0:
		_say("Nobody is talking to you.")
		return

	var index := option_number - 1
	if index < 0 or index >= _topics.size():
		_say("There is no option %d." % option_number)
		return

	ConnectionManager.answer_conversation(_speaker_entity_id, _topics[index])


func _on_conversation_received(message) -> void:
	_speaker_entity_id = message.SpeakerEntityId
	_topics.clear()

	_say("%s: %s" % [message.SpeakerName, _resolve(message.Speech)])

	var number := 1
	for option in message.Options:
		_topics.append(option.TopicId)
		_say("  %d) %s" % [number, _resolve(option.Line)])
		number += 1

	if message.Options.is_empty():
		_say("  (they have nothing more to say)")
	else:
		_say("  /pick <n> to answer")


## A line's key and arguments, as text.
##
## Straight through [DialogText], which already does exactly this for the dialog popup - translate the
## key, then treat the result as a template. Reusing it rather than copying it is what keeps the
## nested-token lookup, the item and skill name resolution, and this stub from drifting apart.
func _resolve(line) -> String:
	return DialogText.resolve_line(line.Key, Array(line.Args))


func _say(text: String) -> void:
	if _chat != null:
		_chat.system_line(text)
	else:
		print(text)
