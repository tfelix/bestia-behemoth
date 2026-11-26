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
	if enchantment.EndsAtMsec == 0:
		return enchantment_name
	return "%s · {ends_at_msec:%d}" % [enchantment_name, enchantment.EndsAtMsec]


## [param text] with every deadline mark turned into the time left now.
static func with_time_left(text: String) -> String:
	var now := Time.get_ticks_msec()
	var shown := text
	for mark in _deadline_mark.search_all(text):
		var seconds_left := maxf((mark.get_string(1).to_int() - now) / 1000.0, 0.0)
		var time_left := TranslationServer.translate("ITEM_ENCHANTMENT_TIME_LEFT").format(
			{"time": DurationText.short(seconds_left)}
		)
		shown = shown.replace(mark.get_string(), time_left)
	return shown
