class_name ItemEnchantmentText
## Writes out an enchantment on a held item: its name, and the time left for one that ends.
##
## A tooltip is written when its row is built but read whenever the pointer comes by. So [method marked_line]
## leaves a deadline mark where the time left goes, and [method with_time_left] fills it in when the text is shown.
##
## Static, so the lookups go through [TranslationServer] for the reason [DialogText] gives.

static var _deadline_mark := RegEx.create_from_string("\\{ends_at_msec:(\\d+)\\}")


## "Postal Seal", or "Endow Fire · 2 min left" as of now.
static func line(enchantment) -> String:
	return with_time_left(marked_line(enchantment))


## Like [method line], with a deadline mark in place of the time left.
static func marked_line(enchantment) -> String:
	var enchantment_name := TranslationServer.translate("ITEM_ENCHANTMENT_%d" % enchantment.EnchantmentId)
	if not enchantment.Element.is_empty():
		var element_name := TranslationServer.translate("ELEMENT_" + enchantment.Element)
		enchantment_name = enchantment_name.format({"element": element_name})
	if enchantment.EndsAtMsec == 0:
		return enchantment_name
	return "%s · {ends_at_msec:%d}" % [enchantment_name, enchantment.EndsAtMsec]


## [param text] with every deadline mark turned into the time left now.
##
## Past the deadline it says the enchantment wore off: the server takes an ended one off worn gear at once, but a
## copy lying in the bag keeps it until the bag is sent again.
static func with_time_left(text: String) -> String:
	var now := Time.get_ticks_msec()
	var shown := text
	for mark in _deadline_mark.search_all(text):
		var seconds_left := (mark.get_string(1).to_int() - now) / 1000.0
		var time_left := TranslationServer.translate("ITEM_ENCHANTMENT_ENDED")
		if seconds_left > 0.0:
			time_left = TranslationServer.translate("ITEM_ENCHANTMENT_TIME_LEFT").format(
				{"time": DurationText.short(seconds_left)}
			)
		shown = shown.replace(mark.get_string(), time_left)
	return shown
