class_name AuctionFormat
extends RefCounted

## How the auction window writes coins and time, shared by its tabs.


static func coins(amount: int) -> String:
	return UiStyle.group_digits(amount)


## "4d 3h", "5h 20m", "12m". Coarse on purpose: a listing ends within the hour or the day, not the second.
static func time_left(expires_at_epoch_seconds: int) -> String:
	var seconds := maxi(0, expires_at_epoch_seconds - int(Time.get_unix_time_from_system()))
	var minutes := seconds / 60
	var hours := minutes / 60
	var days := hours / 24

	if days > 0:
		return "%dd %dh" % [days, hours % 24]
	if hours > 0:
		return "%dh %dm" % [hours, minutes % 60]
	return "%dm" % maxi(1, minutes)


## A small gold disc that marks an amount as coins. Drawn rather than the coin item's icon, which has no art.
static func coin_icon(size: int = 10) -> Panel:
	var coin := Panel.new()
	coin.custom_minimum_size = Vector2(size, size)
	coin.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	coin.mouse_filter = Control.MOUSE_FILTER_IGNORE
	coin.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ACCENT, size / 2, 0, Color(0.6, 0.45, 0.1), 1))
	return coin


## A right-aligned amount with the coin after it, for a price column.
static func price(amount: int, width: int, color: Color = UiStyle.ACCENT) -> HBoxContainer:
	var box := UiStyle.hbox(3)
	box.custom_minimum_size = Vector2(width, 0)
	box.alignment = BoxContainer.ALIGNMENT_END
	box.add_child(UiStyle.label(coins(amount), 12, color))
	box.add_child(coin_icon())
	return box
