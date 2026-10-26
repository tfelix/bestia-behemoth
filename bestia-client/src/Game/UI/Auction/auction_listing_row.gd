class_name AuctionListingRow
extends PanelContainer

## One listing on the board: what it is, who sells it, how long it stays, and what it costs.

signal buy_pressed(listing: AuctionListingEntry)

## Column widths, shared with the header so the two line up.
const COLUMNS := {"level": 36, "amount": 40, "seller": 110, "time": 56, "unit": 80, "price": 92, "action": 64}

var _listing: AuctionListingEntry
var _item: ItemResource


func _init(listing: AuctionListingEntry, item: ItemResource) -> void:
	_listing = listing
	_item = item
	mouse_filter = Control.MOUSE_FILTER_PASS
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 3, 5))
	mouse_entered.connect(func() -> void: add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_HOVER_BG, 3, 5)))
	mouse_exited.connect(func() -> void: add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 3, 5)))
	# Any text: Godot asks for the custom tooltip below only when there is one.
	tooltip_text = "item"
	_build()


## Builds the columns; the header uses the same widths, see [constant COLUMNS].
static func header() -> Control:
	var row := UiStyle.hbox(8)
	row.add_child(_cell(UiStyle.label("    " + TranslationServer.translate("AUCTION_COL_ITEM"), 11, UiStyle.MUTED), 0))
	row.get_child(0).size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for key in ["level", "amount", "seller", "time", "unit", "price"]:
		var label := UiStyle.label(TranslationServer.translate("AUCTION_COL_" + key.to_upper()), 11, UiStyle.MUTED)
		if key in ["unit", "price"]:
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

	var seller := UiStyle.vbox(0)
	seller.add_child(UiStyle.label(_listing.SellerName, 12))
	seller.add_child(UiStyle.label(_listing.TownName, 10, UiStyle.MUTED))
	row.add_child(_cell(seller, COLUMNS["seller"]))

	row.add_child(_cell(UiStyle.label(AuctionFormat.time_left(_listing.ExpiresAtEpochSeconds), 12, UiStyle.MUTED), COLUMNS["time"]))
	var unit := int(ceil(float(_listing.Price) / maxi(1, _listing.Amount)))
	row.add_child(AuctionFormat.price(unit, COLUMNS["unit"], UiStyle.MUTED))
	row.add_child(AuctionFormat.price(_listing.Price, COLUMNS["price"]))

	var buy := Button.new()
	buy.text = tr("AUCTION_BUY")
	buy.custom_minimum_size = Vector2(COLUMNS["action"], 0)
	buy.pressed.connect(func() -> void: buy_pressed.emit(_listing))
	row.add_child(buy)


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
