class_name Hotkeys

## The key an input action is bound to, worded the way the player's keyboard prints it: "Alt+I", "Escape".
##
## Bindings are physical (US positions), so the key is translated to the player's own layout first - the key
## right of T reads Z on a German keyboard - the same translation the shortcut slots make for their labels.
## Empty when no key is bound to [param action].
static func label_of(action: StringName) -> String:
	if not InputMap.has_action(action):
		return ""

	for event in InputMap.action_get_events(action):
		var key := event as InputEventKey
		if key == null:
			continue
		if key.physical_keycode == KEY_NONE:
			return key.as_text_keycode()

		var shown := key.duplicate() as InputEventKey
		shown.keycode = DisplayServer.keyboard_get_keycode_from_physical(key.physical_keycode)
		return shown.as_text_keycode()

	return ""
