class_name DialogText
## Turns a [DialogSMSG] into displayable, localized text.
##
## The server never sends text - only a dialog id and typed placeholder values - so everything
## visible is assembled here: the sentence comes from [code]dialogs.csv[/code], and each
## [code]{placeholder}[/code] in it is filled from the message's args.
##
## This is the first place in the client that treats a translated string as a [i]template[/i].
## Everywhere else builds a hardcoded English sentence around a translated fragment (see
## [code]drop_amount_dialog.gd[/code]'s "Drop how many %s?"), which cannot be translated as a whole.
## New user-facing text should follow the pattern here instead.
##
## Note these are [i]static[/i] helpers, so they go through [TranslationServer] rather than
## [method Object.tr] - [code]tr()[/code] is an instance method and is not callable without one.


## Resolves the body text. Returns the raw translation key if it is missing from
## [code]dialogs.csv[/code], which is the default lookup behaviour and exactly what we want in
## development - a missing dialog is loud on screen rather than silently blank.
static func resolve(message) -> String:
	return _format(_translate(text_key(message.DialogId)), message)


## Resolves the window title, or an empty string if this dialog has no [code]_TITLE[/code] row.
## Titles are optional by design: plenty of dialogs are just a line of text.
static func resolve_title(message) -> String:
	var key := title_key(message.DialogId)
	var translated := _translate(key)

	# The lookup echoes the key back when there is no entry for it, which is how we detect "no title".
	if translated == key:
		return ""

	return _format(translated, message)


static func _translate(key: String) -> String:
	return TranslationServer.translate(key)


static func text_key(dialog_id: int) -> String:
	return "DIALOG_%d_TEXT" % dialog_id


static func title_key(dialog_id: int) -> String:
	return "DIALOG_%d_TITLE" % dialog_id


static func _format(template: String, message) -> String:
	var values := {}

	for arg in message.Args:
		values[arg.Name] = _resolve_arg(arg)

	return template.format(values)


## Renders one placeholder value. Item and skill args deliberately arrive as ids rather than
## finished strings so their names get localized here, out of the client's own DBs, instead of
## being frozen into whatever language the server happened to use.
static func _resolve_arg(arg) -> String:
	match arg.KindName:
		"text":
			return arg.Text
		"number":
			return str(arg.Number)
		"item":
			var item := ItemDB.get_instance().get_item(int(arg.Number))
			if item == null:
				printerr("DialogText: unknown item id %d in dialog arg '%s'" % [arg.Number, arg.Name])
				return "?"
			return _translate(item.name_key)
		"skill":
			var attack := AttackDB.get_instance().get_attack(int(arg.Number))
			if attack == null:
				printerr("DialogText: unknown skill id %d in dialog arg '%s'" % [arg.Number, arg.Name])
				return "?"
			return attack.name
		"entity":
			return _resolve_entity_name(int(arg.Number))
		_:
			printerr("DialogText: unhandled dialog arg kind '%s' for '%s'" % [arg.KindName, arg.Name])
			return "?"


## Entities have no display name on the client yet - [Entity] carries no name and [NameTag] is a
## bare sprite - so this can only confirm the entity is known and otherwise degrade visibly. Once
## entities do carry a name, this is the single place that needs to change.
static func _resolve_entity_name(entity_id: int) -> String:
	var manager := EntityManager.get_instance()
	if manager == null or manager.get_entity(entity_id) == null:
		printerr("DialogText: dialog referenced unknown entity %d" % entity_id)

	return "???"
