extends VBoxContainer
class_name ObtainedItems
## What just arrived in the bag, said over the world for a few seconds. Top centre, under the location panel.
##
## Says only that something was obtained, never how it was come by: a gain is detected by diffing inventory
## snapshots (see [signal Inventory.items_gained]), and a diff cannot tell a pickup from a craft, a trade or
## a purchase.

## One row's whole life, in seconds. Longer than a damage tag's because this is read rather than glanced at.
const _FADE_IN := 0.15
const _HOLD := 3.0
const _FADE_OUT := 0.6

## Rows standing at once. Emptying a full corpse should not become a column down the screen.
const _MAX_ROWS := 5

const _ICON_SIZE := Vector2(24, 24)

## Dark outline, so a light item name stays legible against snow or sand.
const _OUTLINE_SIZE := 4
const _FONT_SIZE := 16


## Puts up one row per gain, each entry [code]{item: ItemResource, amount: int}[/code].
func show_gains(gains: Array) -> void:
	for gain in gains:
		_add_row(gain["item"], gain["amount"])


## Rows are built here rather than instanced from a scene of their own: two controls and a label do not earn
## a scene, a script and a uid. The same call chat.gd makes for its lines.
func _add_row(item: ItemResource, amount: int) -> void:
	var row := HBoxContainer.new()
	row.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(_icon_of(item))
	row.add_child(_label_for(item, amount))
	add_child(row)

	# Removed before it is freed, so a second gain in the same frame already counts one row fewer.
	while get_child_count() > _MAX_ROWS:
		var oldest := get_child(0)
		remove_child(oldest)
		oldest.queue_free()

	_play_fade(row)


## Click-through, like every node here: this is drawn over the world and the ground behind it is still the
## player's to click, which is also why the widget stays out of "world_blocking_ui". A [TextureRect] defaults
## to stopping the mouse, so it is the one that would silently start eating clicks.
func _icon_of(item: ItemResource) -> TextureRect:
	var icon := TextureRect.new()
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon.texture = item.get_icon()
	icon.custom_minimum_size = _ICON_SIZE
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED

	return icon


func _label_for(item: ItemResource, amount: int) -> Label:
	var label := Label.new()
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	label.text = tr("ITEM_OBTAINED") % (tr("ITEM_OBTAINED_AMOUNT") % [amount, tr(item.name_key)])
	label.add_theme_font_size_override("font_size", _FONT_SIZE)
	label.add_theme_constant_override("outline_size", _OUTLINE_SIZE)
	label.add_theme_color_override("font_outline_color", Color.BLACK)

	return label


## Fades a row in, holds it long enough to read, then fades it out and drops it.
##
## The tween belongs to the row rather than to this node, so a row cut from the top by [constant _MAX_ROWS]
## takes its own tween with it and cannot go on animating something that is gone.
func _play_fade(row: Control) -> void:
	row.modulate.a = 0.0

	var tween := row.create_tween()
	tween.tween_property(row, "modulate:a", 1.0, _FADE_IN)
	tween.tween_property(row, "modulate:a", 0.0, _FADE_OUT).set_delay(_HOLD)
	tween.tween_callback(row.queue_free)
