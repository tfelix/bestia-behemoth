extends VBoxContainer
class_name ItemDetailColumn
## The inventory window's right-hand column: the clicked item's painting, then what the item is laid out to be
## read at leisure - name and kind, a table of its facts, a wear bar, its bonuses, and its description last.
##
## It reads the row it is handed rather than a second copy of the item, including what the row learned after
## it was drawn (a scroll's spell, an egg's contents). The inventory keeps it current: it hands over the new row
## after each re-render and closes the column once the item is no longer held.
##
## The wording comes from [ItemDetail] wherever the tooltip already says the same thing, so the two never drift.

## The column was closed, by its button or because its item went away.
signal closed()

const _UPGRADE := Color(0.55, 0.88, 0.5)
const _BONUS := Color(0.55, 0.88, 0.5)
const _PARCHMENT := Color(0.88, 0.82, 0.7)
const _RULE := Color(0.6, 0.6, 0.66, 0.25)
const _WEAR_GOOD := Color(0.45, 0.78, 0.42)
const _WEAR_WORN := Color(0.95, 0.72, 0.3)
const _LIGHT := Color(1.0, 0.7, 0.35)

## What the column shows, or 0 while it is closed. Names the held pile or copy as
## [member InventoryItemResource.player_item_id] does, since the rows themselves are rebuilt on every update.
var shown_player_item_id := 0

@onready var _title: RichTextLabel = %Title
@onready var _subtitle: Label = %Subtitle
@onready var _picture: TextureRect = %Picture
@onready var _scroll: ScrollContainer = %Scroll
@onready var _content: VBoxContainer = %Content


func _ready() -> void:
	%Close.pressed.connect(close)


## [param row] is an inventory row (InventoryItem); untyped for the load cycle that row's own script explains.
func show_row(row) -> void:
	var item: ItemResource = row.item
	var switched: bool = row.player_item_id != shown_player_item_id
	shown_player_item_id = row.player_item_id

	_show_title(item, row.title, row.upgrade_level)
	_show_kind(item, row.item_level)
	_show_picture(item)

	for child in _content.get_children():
		_content.remove_child(child)
		child.queue_free()

	_add_facts(item, row.slots)
	_add_wear(item, row.durability, row.max_durability)
	_add_bonuses(row.rolled_stats)
	_add_rich(row.extra_detail, UiStyle.TEXT)
	_add_origin(item, row.crafted_by, row.crafted_in)
	_add_description(item)

	if switched:
		_scroll.scroll_vertical = 0
	show()


func close() -> void:
	if shown_player_item_id == 0:
		return

	shown_player_item_id = 0
	hide()
	closed.emit()


func _show_title(item: ItemResource, own_name: String, upgrade_level: int) -> void:
	var title := own_name if not own_name.is_empty() else tr(item.name_key)
	if upgrade_level > 0:
		title += " [color=#%s]+%d[/color]" % [_UPGRADE.to_html(false), upgrade_level]
	_title.text = title


## "Greatsword · Lv. 22": what decides who may use it, then its tier. Tier 1 says nothing and is left off, as
## the tooltip does.
func _show_kind(item: ItemResource, item_level: int) -> void:
	var parts: Array[String] = []
	if not item.weapon_type.is_empty():
		parts.append(tr("ITEM_WEAPON_%s" % item.weapon_type))
	elif item.armor_type > 0:
		parts.append(tr("ITEM_ARMOR_TYPE_%d" % item.armor_type))

	var level := item_level if item_level > 0 else item.level
	if level > 1:
		parts.append(tr("ITEM_DETAIL_LEVEL").format({"level": level}))

	_subtitle.text = "  ·  ".join(parts)
	_subtitle.visible = not parts.is_empty()


## An item not painted yet shows its icon instead. The icons are pixel art, which smears when filtered up to
## this size, while the paintings are shrunk from larger art and want the smooth filter.
func _show_picture(item: ItemResource) -> void:
	if item.illustration != null:
		_picture.texture = item.illustration
		_picture.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	else:
		_picture.texture = item.get_icon()
		_picture.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST


## Name on the left, value on the right; only the facts this item has.
func _add_facts(item: ItemResource, slots: int) -> void:
	var grid := GridContainer.new()
	grid.columns = 2
	grid.add_theme_constant_override("v_separation", 3)

	if item.min_level > 1:
		_add_fact(grid, tr("ITEM_DETAIL_REQUIRED_LEVEL"), _value(str(item.min_level)))
	if item.attack_range > 0:
		_add_fact(grid, tr("ITEM_DETAIL_RANGE"), _value(tr("ITEM_DETAIL_RANGE_TILES").format({"tiles": item.attack_range})))
	# items.yml weighs in hundredths of a kilogram, as the exchange column's carry line reads it.
	_add_fact(grid, tr("ITEM_DETAIL_WEIGHT"), _value(tr("ITEM_DETAIL_WEIGHT_KG").format({"kg": String.num(item.weight / 100.0, 2)})))
	if slots > 0:
		_add_fact(grid, tr("ITEM_DETAIL_SOCKETS"), _sockets(slots))

	_content.add_child(grid)


func _add_fact(grid: GridContainer, fact: String, value: Control) -> void:
	var key := UiStyle.label(fact, 13, UiStyle.MUTED)
	key.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	grid.add_child(key)
	grid.add_child(value)


func _value(text: String) -> Label:
	var label := UiStyle.label(text, 13, UiStyle.TEXT)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	return label


## One empty ring per socket: nothing can fill one yet, so every ring is open.
func _sockets(slots: int) -> HBoxContainer:
	var rings := UiStyle.hbox(4)
	rings.alignment = BoxContainer.ALIGNMENT_END
	for i in slots:
		var ring := Panel.new()
		ring.custom_minimum_size = Vector2(11, 11)
		ring.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		ring.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.BAR_BG, 6, 0, UiStyle.ACCENT, 1))
		rings.add_child(ring)
	return rings


## A bar that turns from green through amber to red as the item wears. A light's wear is the time it has
## left to burn, which reads better as a share than as points - the tooltip says it the same way.
func _add_wear(item: ItemResource, durability: int, max_durability: int) -> void:
	if max_durability <= 0:
		return

	var share := clampf(float(durability) / max_durability, 0.0, 1.0)
	var heading := UiStyle.hbox(6)
	var value: Label
	var fill: Color
	if item.gives_light():
		heading.add_child(UiStyle.label(tr("ITEM_DETAIL_LIGHT_LEFT"), 13, UiStyle.MUTED))
		value = UiStyle.label("%d%%" % roundi(100.0 * share), 13, UiStyle.TEXT)
		fill = _LIGHT
	else:
		heading.add_child(UiStyle.label(tr("ITEM_DETAIL_DURABILITY"), 13, UiStyle.MUTED))
		if durability <= 0:
			value = UiStyle.label(tr("ITEM_DETAIL_BROKEN"), 13, UiStyle.DANGER)
		else:
			value = UiStyle.label("%d / %d" % [durability, max_durability], 13, UiStyle.TEXT)
		fill = _WEAR_GOOD if share > 0.5 else _WEAR_WORN if share > 0.2 else UiStyle.DANGER
	heading.add_child(UiStyle.spacer())
	heading.add_child(value)

	var bar := UiStyle.bar(fill, 6)
	UiStyle.set_bar(bar, durability, max_durability)

	var block := UiStyle.vbox(4)
	block.add_child(heading)
	block.add_child(bar)
	_content.add_child(block)


func _add_bonuses(rolled_stats: Array) -> void:
	if rolled_stats.is_empty():
		return

	var block := UiStyle.vbox(2)
	block.add_child(UiStyle.label(tr("ITEM_DETAIL_BONUSES"), 13, UiStyle.MUTED))
	for rolled in rolled_stats:
		block.add_child(UiStyle.label(ItemDetail.stat_line(rolled.Stat, rolled.Value), 14, _BONUS))
	_content.add_child(block)


## Material axes and the crafter, small and quiet: true of this copy, but rarely what the player came to read.
func _add_origin(item: ItemResource, crafted_by: String, crafted_in: String) -> void:
	var lines: Array[String] = []
	var material_axes := CraftAxes.describe(item.item_id)
	if not material_axes.is_empty():
		lines.append(tr("ITEM_MATERIAL_AXES").format({"axes": material_axes}))
	if not crafted_by.is_empty():
		lines.append(tr("ITEM_CRAFTED_BY").format({"crafter": crafted_by, "world": crafted_in}))
	if lines.is_empty():
		return

	var label := UiStyle.label("\n".join(lines), 12, UiStyle.MUTED)
	label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_content.add_child(label)


## Set off by a rule, slanted and in a warmer ink: the item speaking for itself, after the numbers.
func _add_description(item: ItemResource) -> void:
	var description := ItemDetail.description_of(item)
	if description.is_empty():
		return

	var rule := ColorRect.new()
	rule.color = _RULE
	rule.custom_minimum_size = Vector2(0, 1)
	_content.add_child(rule)

	var slanted := FontVariation.new()
	slanted.base_font = get_theme_default_font()
	slanted.variation_transform = Transform2D(Vector2(1, 0), Vector2(0.2, 1), Vector2.ZERO)

	var text := _add_rich(description, _PARCHMENT)
	text.add_theme_font_override("normal_font", slanted)
	text.add_theme_font_size_override("normal_font_size", 14)
	text.add_theme_constant_override("line_separation", 3)


func _add_rich(bbcode: String, color: Color) -> RichTextLabel:
	if bbcode.is_empty():
		return null

	var text := RichTextLabel.new()
	text.bbcode_enabled = true
	text.fit_content = true
	text.scroll_active = false
	text.add_theme_color_override("default_color", color)
	text.text = bbcode
	_content.add_child(text)
	return text
