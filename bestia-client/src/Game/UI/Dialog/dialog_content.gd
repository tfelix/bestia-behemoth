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


## A client-only dialog, named by a key rather than by a catalogue id.
##
## The key is spelled out in [code]dialogs.csv[/code] as [code]DIALOG_<key>_TEXT[/code] and optionally
## [code]DIALOG_<key>_TITLE[/code], so a local dialog and a server one sit in the same file and are translated
## the same way. Static information that needs nothing from the server has no business being a wire message.
static func of_local(key: String) -> DialogContent:
	return DialogContent.new("DIALOG_%s_TEXT" % key, "DIALOG_%s_TITLE" % key)
