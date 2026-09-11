class_name DialogContent
extends RefCounted
## One dialog waiting to be shown, in the only form the presenter needs: a pair of translation keys plus
## whatever placeholder values go with them.
##
## This is what lets a server-pushed dialog and a client-only one share the whole queue and the whole window.
## A [DialogSMSG] carries an id and typed args; a local dialog carries a hand-written key and nothing. Both
## reduce to "a text key, maybe a title key, maybe some args", and normalising them here is what keeps
## [MessageDialog] from having to ask which kind it is holding.
##
## Deliberately not resolved at construction. Translation stays lazy so a language change between a dialog
## arriving and being read shows the new language, which matters because a dialog can sit in the queue for as
## long as the player leaves the one before it on screen.

## The [code]dialogs.csv[/code] row holding the body text.
var text_key: String

## The row holding the window title, or empty for a dialog that wants the default title. Optional by design:
## plenty of dialogs are just a line of text.
var title_key: String

## Placeholder values, as [code]DialogArg[/code] objects. Always empty for a local dialog - a client-only
## dialog is static text, which is the whole reason it needs no server round trip.
var args: Array

## What the player may say next, as [code]ConversationOption[/code] objects. Empty for everything that is
## not a conversation, which is why the window never has to ask what kind of thing it is holding.
var options: Array = []

## A window title that is already text rather than a key - a speaker's name, which is invented by the
## generator and belongs to no language. Empty for every dialog whose title comes from [member title_key].
var title_override: String = ""

## Called with an option's topic id when the player picks one.
##
## Carried here rather than known by the window, so the window stays a window: it renders options and
## reports which was chosen, and what a choice *means* stays with whoever built the content.
var on_choice: Callable = Callable()


func _init(p_text_key: String, p_title_key: String = "", p_args: Array = []) -> void:
	text_key = p_text_key
	title_key = p_title_key
	args = p_args


## The content of a server-pushed dialog. Keys are derived from the catalogue id, exactly as before - see
## [DialogText].
static func of_message(message) -> DialogContent:
	return DialogContent.new(
		DialogText.text_key(message.DialogId),
		DialogText.title_key(message.DialogId),
		message.Args
	)


## The content of one step of a conversation.
##
## The speech becomes the body and the options become buttons. The key is carried outright rather than
## derived from a catalogue id, because it is computed from an event kind - see [DialogText.resolve_line].
static func of_conversation(message, choose: Callable) -> DialogContent:
	var content := DialogContent.new(message.Speech.Key, "", Array(message.Speech.Args))
	content.options = Array(message.Options)
	content.title_override = message.SpeakerName
	content.on_choice = choose

	return content


## A client-only dialog, named by a key rather than by a catalogue id.
##
## The key is spelled out in [code]dialogs.csv[/code] as [code]DIALOG_<key>_TEXT[/code] and optionally
## [code]DIALOG_<key>_TITLE[/code], so a local dialog and a server one sit in the same file and are translated
## the same way. Static information that needs nothing from the server has no business being a wire message.
static func of_local(key: String) -> DialogContent:
	return DialogContent.new("DIALOG_%s_TEXT" % key, "DIALOG_%s_TITLE" % key)
