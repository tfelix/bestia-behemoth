extends VBoxContainer
class_name CarvingTableDepositPage
## The Carving Table's page for every master, Artificer or not: lock one of your Bestias into the table, so an
## Artificer can etch it, or take it back out. English like the rest of the crafting window.

signal deposit_requested(player_bestia_id: int)
signal take_back_requested(player_bestia_id: int)

const _STORAGE_ICON := preload("res://Theme/Icons/chest.svg")
const _STORAGE_ICON_SIZE := 20


## [param deposited] are every master's [DepositedBestia]s, [param depositable] the player's own [DepositableBestia]s.
func show_bestias(deposited: Array, depositable: Array) -> void:
	for child in get_children():
		remove_child(child)
		child.queue_free()

	add_child(UiStyle.label("Locked into this table", 13))
	var own := deposited.filter(func(bestia: DepositedBestia) -> bool: return bestia.Mine)
	if own.is_empty():
		add_child(UiStyle.label("None of your Bestias.", 12, UiStyle.MUTED))
	for bestia in own:
		add_child(_locked_row(bestia))

	add_child(HSeparator.new())
	add_child(UiStyle.label("Your Bestias", 13))
	if depositable.is_empty():
		add_child(UiStyle.label("You have no Bestia to lock in.", 12, UiStyle.MUTED))
	for bestia in depositable:
		add_child(_depositable_row(bestia))

	add_child(HSeparator.new())
	var note := UiStyle.label(
		"A locked Bestia waits here, out of the world, until an Artificer etches it. The Essence Gem comes to you by " +
		"post. Taking it back puts it into your storage and calls off its etching.", 12, UiStyle.MUTED)
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	add_child(note)


func _locked_row(bestia: DepositedBestia) -> HBoxContainer:
	var row := UiStyle.hbox(8)
	var text := "%s   Lv %d" % [EtchingPicker.name_of(bestia), int(bestia.Level)]
	if int(bestia.EtchSecondsLeft) > 0:
		text += "   · %s etches it, %s left" % [bestia.EtcherName, EtchingPicker.duration_text(int(bestia.EtchSecondsLeft))]
	var label := UiStyle.label(text, 13)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(label)
	row.add_child(_button("Take back", func() -> void: take_back_requested.emit(int(bestia.PlayerBestiaId))))
	return row


func _depositable_row(bestia: DepositableBestia) -> HBoxContainer:
	var row := UiStyle.hbox(8)
	if bestia.InStorage:
		row.add_child(_storage_icon())
	var label := UiStyle.label("%s   Lv %d" % [EtchingPicker.name_of(bestia), int(bestia.Level)], 13)
	label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(label)
	row.add_child(_button("Lock in", func() -> void: deposit_requested.emit(int(bestia.PlayerBestiaId))))
	return row


func _button(text: String, on_pressed: Callable) -> Button:
	var button := Button.new()
	button.text = text
	button.focus_mode = Control.FOCUS_NONE
	button.pressed.connect(on_pressed)
	return button


func _storage_icon() -> TextureRect:
	var icon := TextureRect.new()
	icon.texture = _STORAGE_ICON
	icon.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	icon.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	icon.custom_minimum_size = Vector2(_STORAGE_ICON_SIZE, _STORAGE_ICON_SIZE)
	icon.modulate = UiStyle.MUTED
	icon.tooltip_text = "In storage"
	return icon
