class_name SettingsManager

## Loads [ClientSettings] once per process: shipped defaults, with the player's own file on top.
##
## Not an autoload and not a node. It has no lifetime to speak of - reading it is what loads it -
## so it is the same lazy singleton [AttackDB] and [ItemDB] use, and callers reach it the same way:
## [code]SettingsManager.get_instance().map_server_url[/code].
##
## [b]The user file is sparse.[/b] It names only what has been changed, so a file written by an
## older build still loads under a newer one that has since added settings - the missing ones keep
## their shipped values instead of the file having to be migrated.

const DEFAULTS_PATH := "res://Settings/default_settings.tres"
const SAVE_PATH := "user://settings.json"

## Build identity, sent in the authentication handshake. Deliberately not a setting: the server
## keeps its own version and compares against this, so a client that could edit it into a user file
## could misreport what it is.
const VERSION := "bclient/0.0.1-alpha"

static var _instance: ClientSettings = null


static func get_instance() -> ClientSettings:
	if _instance == null:
		_instance = _load_defaults()
		_apply_user_overrides(_instance)
	return _instance


## Writes the current values to [constant SAVE_PATH]. Every setting is written, not just the changed
## ones - what makes the file sparse is that a hand-edited or older one may omit any of them.
static func save() -> void:
	var settings := get_instance()
	var data := {"format_version": ClientSettings.FORMAT_VERSION}
	for property in _stored_properties(settings):
		data[property.name] = settings.get(property.name)

	var file := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if file == null:
		push_warning("SettingsManager: could not write %s" % SAVE_PATH)
		return

	file.store_string(JSON.stringify(data, "\t"))
	file.close()


## Forgets the loaded settings, so the next [method get_instance] reads from disk again.
static func reload() -> void:
	_instance = null


static func _load_defaults() -> ClientSettings:
	var defaults := load(DEFAULTS_PATH) as ClientSettings
	if defaults == null:
		# The client still has to boot, and ClientSettings' own property defaults are the same values
		# the resource holds - so this costs a working client only if the two ever drift.
		push_error("SettingsManager: could not load %s; using built-in defaults" % DEFAULTS_PATH)
		return ClientSettings.new()

	# The loaded resource is shared with anything else that loads the same path, and settings are
	# writable, so edits here must not reach back into the cached resource.
	return defaults.duplicate() as ClientSettings


static func _apply_user_overrides(settings: ClientSettings) -> void:
	if not FileAccess.file_exists(SAVE_PATH):
		return

	var file := FileAccess.open(SAVE_PATH, FileAccess.READ)
	if file == null:
		push_warning("SettingsManager: could not read %s" % SAVE_PATH)
		return

	var json := JSON.new()
	var error := json.parse(file.get_as_text())
	file.close()

	# A corrupt or hand-edited file costs the player their preferences, not their launch.
	if error != OK:
		push_warning("SettingsManager: could not parse %s: %s" % [SAVE_PATH, json.get_error_message()])
		return

	var stored = json.data
	if typeof(stored) != TYPE_DICTIONARY:
		push_warning("SettingsManager: discarding %s, which is not a settings file" % SAVE_PATH)
		return

	if int(stored.get("format_version", 0)) != ClientSettings.FORMAT_VERSION:
		push_warning("SettingsManager: discarding %s, written in an older format" % SAVE_PATH)
		return

	for property in _stored_properties(settings):
		if not stored.has(property.name):
			continue
		# Converted rather than assigned: JSON has one number type, so an int setting arrives as a
		# float and every enum-ish value would land as the wrong type.
		settings.set(property.name, type_convert(stored[property.name], property.type))


## The settings themselves, without the bookkeeping Godot adds to every Resource.
static func _stored_properties(settings: ClientSettings) -> Array:
	return settings.get_property_list().filter(
		func(property): return property.usage & PROPERTY_USAGE_SCRIPT_VARIABLE
	)
