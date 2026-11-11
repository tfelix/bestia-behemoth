class_name StallListingRow
extends PanelContainer

## One offer on a stall: what it is, how many, and what the lot costs. The button buys it, or takes it back
## for the seller.

signal action_pressed(listing: StallListingEntry)

## Column widths, shared with the header so the two line up.
const COLUMNS := {"level": 36, "amount": 44, "price": 96, "action": 84}

var _listing: StallListingEntry
var _item: ItemResource
var _action_text: String


func _init(listing: StallListingEntry, item: ItemResource, action_text: String) -> void:
	_listing = listing
	_item = item
	_action_text = action_text
	mouse_filter = Control.MOUSE_FILTER_PASS
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 3, 5))
	mouse_entered.connect(func() -> void: add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_HOVER_BG, 3, 5)))
	mouse_exited.connect(func() -> void: add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 3, 5)))
	# Any text: Godot asks for the custom tooltip below only when there is one.
	tooltip_text = "item"
	_build()


static func header() -> Control:
	var row := UiStyle.hbox(8)
	var item := UiStyle.label("    " + TranslationServer.translate("STALL_COL_ITEM"), 11, UiStyle.MUTED)
	item.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(item)
	for key in ["level", "amount", "price"]:
		var label := UiStyle.label(TranslationServer.translate("STALL_COL_" + key.to_upper()), 11, UiStyle.MUTED)
		if key == "price":
			label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		row.add_child(_cell(label, COLUMNS[key]))
	row.add_child(_cell(Control.new(), COLUMNS["action"]))
	return UiStyle.margin(row, 5, 0, 5, 0)


func _build() -> void:
	var row := UiStyle.hbox(8)
	add_child(row)

	var name_box := UiStyle.hbox(6)
	name_box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var icon := TextureRect.new()
	icon.texture = _item.get_icon()
	icon.custom_minimum_size = Vector2(28, 28)
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	name_box.add_child(icon)
	var title := tr(_item.name_key)
	if _listing.UpgradeLevel > 0:
		title += " +%d" % _listing.UpgradeLevel
	var name_label := UiStyle.label(title, 13)
	name_label.clip_text = true
	name_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_box.add_child(name_label)
	row.add_child(name_box)

	var level := _listing.ItemLevel if _listing.ItemLevel > 0 else _item.level
	row.add_child(_cell(UiStyle.label(str(level), 12, UiStyle.MUTED), COLUMNS["level"]))
	row.add_child(_cell(UiStyle.label("×%d" % _listing.Amount, 12), COLUMNS["amount"]))
	row.add_child(AuctionFormat.price(_listing.Price, COLUMNS["price"]))

	var action := Button.new()
	action.text = _action_text
	action.custom_minimum_size = Vector2(COLUMNS["action"], 0)
	action.pressed.connect(func() -> void: action_pressed.emit(_listing))
	row.add_child(action)


static func _cell(control: Control, width: int) -> Control:
	control.custom_minimum_size = Vector2(width, 0)
	control.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return control


## The copy's own state, as the inventory shows it. BBCode, so a RichTextLabel stands in for the plain tooltip.
func _make_custom_tooltip(_for_text: String) -> Object:
	var label := RichTextLabel.new()
	label.bbcode_enabled = true
	label.fit_content = true
	label.scroll_active = false
	label.custom_minimum_size = Vector2(320, 0)
	label.text = ItemDetail.as_bbcode(
		_item, _listing.UpgradeLevel, _listing.Durability, _listing.MaxDurability, _listing.Slots,
		_listing.ItemLevel, _listing.RolledStats
	)
	return label
