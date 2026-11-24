class_name BestiaStoragePanel
extends PanelContainer

## The bestia storage: the stored bestias on the left, the picked one on the right with Summon, Swap and Release.
## A swap first picks the party bestia to send in and warns about everything it carries.
##
## Opened by every BestiaStorageSMSG: the Bestia Ledger asks for one, and a keeper's counter sends one unasked.
## Built in code, like [PostOfficePanel].

signal storage_opened()

## How long Release waits for its second click.
const _RELEASE_CONFIRM_SECONDS := 3.0
## Must match the server's BestiaStorageRows.REST.
const _REST_MINUTES := 30

## The last storage the server sent; null until one arrives.
var _storage: BestiaStorageSMSG
var _picked_id: int = 0
## The party bestia a swap would send into storage; 0 while no swap is being set up.
var _swap_out_entity_id: int = 0
## Free and worn item counts per owned entity, from their InventoryComponentSMSG: {entity_id: [free, worn]}.
var _item_counts: Dictionary = {}

var _party_count: Label
var _storage_count: Label
var _filter: LineEdit
var _rows: VBoxContainer
var _empty: Label
var _detail: VBoxContainer
var _nothing_picked: Label
var _title: Label
var _subtitle: Label
var _health: ProgressBar
var _mana: ProgressBar
var _stamina: ProgressBar
var _rest: Label
var _summon: Button
var _swap: Button
var _release: Button
var _swap_box: VBoxContainer
var _swap_title: Label
var _party: HBoxContainer
var _swap_warning: Label
var _swap_where: Label
var _swap_confirm: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	_build()

	ConnectionManager.bestia_storage_received.connect(_on_storage)
	ConnectionManager.entity_received.connect(_on_entity)
	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.owned_bestias_changed.connect(func(_bestias: Array[BestiaInfo]) -> void: _render_swap())

	var tick := Timer.new()
	tick.wait_time = 1.0
	tick.timeout.connect(_count_down_rest)
	add_child(tick)
	tick.start()


func _on_storage(msg: BestiaStorageSMSG) -> void:
	var was_shown := _storage != null and is_visible_in_tree()
	_storage = msg
	if not _stored_ids().has(_picked_id):
		_picked_id = 0
		_swap_out_entity_id = 0
	_render()

	if not was_shown:
		storage_opened.emit()


func _on_entity(msg: EntitySMSG) -> void:
	if not (msg is InventoryComponentSMSG):
		return

	var free := 0
	var worn := 0
	for item in msg.Items:
		if item.Equipped:
			worn += 1
		else:
			free += 1
	_item_counts[msg.EntityId] = [free, worn]
	_render_swap()


func _build() -> void:
	var root := UiStyle.vbox(8)
	add_child(UiStyle.margin(root, 10, 8, 10, 10))

	var header := UiStyle.hbox(16)
	_party_count = UiStyle.label("", 13, UiStyle.MUTED)
	_storage_count = UiStyle.label("", 13, UiStyle.MUTED)
	header.add_child(UiStyle.spacer())
	header.add_child(_party_count)
	header.add_child(_storage_count)
	root.add_child(header)

	var columns := HBoxContainer.new()
	columns.add_theme_constant_override("separation", 12)
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	root.add_child(columns)

	var left := UiStyle.vbox(6)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(left)
	_filter = LineEdit.new()
	_filter.placeholder_text = tr("BESTIA_STORAGE_FILTER")
	_filter.clear_button_enabled = true
	_filter.text_changed.connect(func(_text: String) -> void: _render_list())
	left.add_child(_filter)
	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	left.add_child(scroll)
	_rows = UiStyle.vbox(3)
	_rows.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_rows)
	_empty = UiStyle.label(tr("BESTIA_STORAGE_EMPTY"), 13, UiStyle.MUTED)
	_empty.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	left.add_child(_empty)

	var right := UiStyle.vbox(6)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	columns.add_child(right)
	_nothing_picked = UiStyle.label(tr("BESTIA_STORAGE_PICK"), 13, UiStyle.MUTED)
	right.add_child(_nothing_picked)
	_detail = UiStyle.vbox(6)
	right.add_child(_detail)
	_build_detail()
	_swap_box = UiStyle.vbox(6)
	right.add_child(_swap_box)
	_build_swap()


func _build_detail() -> void:
	_title = UiStyle.label("", 16)
	_subtitle = UiStyle.label("", 13, UiStyle.MUTED)
	_detail.add_child(_title)
	_detail.add_child(_subtitle)

	_health = _pool_row("BESTIA_STORAGE_HP", UiStyle.HP_FILL)
	_mana = _pool_row("BESTIA_STORAGE_MANA", UiStyle.MANA_FILL)
	_stamina = _pool_row("BESTIA_STORAGE_STAMINA", UiStyle.EXP_FILL)

	_rest = UiStyle.label("", 12, UiStyle.ACCENT)
	_detail.add_child(_rest)
	_detail.add_child(UiStyle.label(tr("BESTIA_STORAGE_FROZEN"), 12, UiStyle.MUTED))

	_summon = Button.new()
	_summon.pressed.connect(_on_summon_pressed)
	_swap = Button.new()
	_swap.text = tr("BESTIA_STORAGE_SWAP")
	_swap.pressed.connect(_on_swap_pressed)
	_release = Button.new()
	_release.text = tr("BESTIA_STORAGE_RELEASE")
	_release.add_theme_color_override("font_color", UiStyle.DANGER)
	_release.pressed.connect(_on_release_pressed)
	for button in [_summon, _swap, _release]:
		_detail.add_child(button)


func _pool_row(label_key: String, fill: Color) -> ProgressBar:
	var row := UiStyle.hbox(8)
	var caption := UiStyle.label(tr(label_key), 12, UiStyle.MUTED)
	caption.custom_minimum_size.x = 64
	row.add_child(caption)
	var bar := UiStyle.bar(fill, 8)
	bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(bar)
	_detail.add_child(row)
	return bar


func _build_swap() -> void:
	_swap_box.add_child(HSeparator.new())
	_swap_title = UiStyle.label("", 14)
	_swap_box.add_child(_swap_title)
	_party = UiStyle.hbox(6)
	_swap_box.add_child(_party)
	_swap_warning = UiStyle.label("", 12, UiStyle.DANGER)
	_swap_warning.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_swap_box.add_child(_swap_warning)
	_swap_where = UiStyle.label("", 12, UiStyle.MUTED)
	_swap_where.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_swap_box.add_child(_swap_where)

	var buttons := UiStyle.hbox(8)
	buttons.add_child(UiStyle.spacer())
	var cancel := Button.new()
	cancel.text = tr("BESTIA_STORAGE_CANCEL")
	cancel.pressed.connect(_close_swap)
	buttons.add_child(cancel)
	_swap_confirm = Button.new()
	_swap_confirm.text = tr("BESTIA_STORAGE_SWAP_CONFIRM")
	_swap_confirm.pressed.connect(_on_swap_confirmed)
	buttons.add_child(_swap_confirm)
	_swap_box.add_child(buttons)


func _render() -> void:
	if _storage == null:
		return

	_party_count.text = tr("BESTIA_STORAGE_PARTY") % [_storage.ActiveBestiaCount, _storage.ActiveSlotCount]
	_storage_count.text = tr("BESTIA_STORAGE_SLOTS") % [_storage.Bestias.size(), _storage.StorageSlotCount]
	_render_list()
	_render_detail()
	_render_swap()


func _render_list() -> void:
	for child in _rows.get_children():
		child.queue_free()

	var needle := _filter.text.strip_edges().to_lower()
	for stored in _storage.Bestias if _storage != null else []:
		if not needle.is_empty() and not _name_of(stored).to_lower().contains(needle) \
				and not BreedingText.species_name(stored.Info.MobId).to_lower().contains(needle):
			continue
		_rows.add_child(_row_for(stored))
	_empty.visible = _storage != null and _storage.Bestias.is_empty()


func _row_for(stored: StoredBestia) -> Button:
	var row := Button.new()
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.toggle_mode = true
	row.button_pressed = int(stored.Info.PlayerBestiaId) == _picked_id
	row.text = "%s %s\n%s" % [_name_of(stored), BreedingText.sex_mark(stored.Info.Sex), _species_line(stored)]
	if stored.RestingSeconds > 0:
		row.text += "  ·  " + _rest_text(stored.RestingSeconds)
	row.pressed.connect(_pick.bind(int(stored.Info.PlayerBestiaId)))
	return row


func _pick(player_bestia_id: int) -> void:
	_picked_id = player_bestia_id
	_swap_out_entity_id = 0
	_swap_box.visible = false
	_render_list()
	_render_detail()


func _render_detail() -> void:
	var stored := _picked()
	_nothing_picked.visible = stored == null
	_detail.visible = stored != null
	if stored == null:
		_swap_box.visible = false
		return

	_title.text = "%s %s" % [_name_of(stored), BreedingText.sex_mark(stored.Info.Sex)]
	_subtitle.text = _species_line(stored)
	_subtitle.tooltip_text = BreedingText.iv_lines(stored.Info.Ivs)
	_subtitle.mouse_filter = Control.MOUSE_FILTER_PASS
	UiStyle.set_bar(_health, stored.Health, stored.MaxHealth)
	UiStyle.set_bar(_mana, stored.Mana, stored.MaxMana)
	UiStyle.set_bar(_stamina, stored.Stamina, stored.MaxStamina)
	_rest.text = _rest_text(stored.RestingSeconds) if stored.RestingSeconds > 0 else ""

	var party_full := _storage.ActiveBestiaCount >= _storage.ActiveSlotCount
	var resting := stored.RestingSeconds > 0
	_summon.text = tr("BESTIA_STORAGE_SUMMON_FULL") if party_full else tr("BESTIA_STORAGE_SUMMON")
	_summon.disabled = party_full or resting
	_swap.disabled = not party_full or resting
	_swap.tooltip_text = "" if party_full else tr("BESTIA_STORAGE_SWAP_SLOT_FREE")
	_release.text = tr("BESTIA_STORAGE_RELEASE")


func _render_swap() -> void:
	var stored := _picked()
	if stored == null or not _swap_box.visible:
		return

	_swap_title.text = tr("BESTIA_STORAGE_SWAP_TITLE") % _name_of(stored)
	for child in _party.get_children():
		child.queue_free()
	for bestia in _owned_bestias():
		var button := Button.new()
		button.toggle_mode = true
		button.button_pressed = int(bestia.EntityId) == _swap_out_entity_id
		button.text = "%s\n%s" % [OwnedSlots.name_of(bestia), tr("BESTIA_STORAGE_LEVEL") % bestia.Level]
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.pressed.connect(_pick_swap_out.bind(int(bestia.EntityId)))
		_party.add_child(button)

	var leaving := _owned_bestia(_swap_out_entity_id)
	_swap_confirm.disabled = leaving == null
	if leaving == null:
		_swap_warning.text = ""
		_swap_where.text = tr("BESTIA_STORAGE_SWAP_PICK")
		return

	var leaving_name := OwnedSlots.name_of(leaving)
	var counts: Array = _item_counts.get(_swap_out_entity_id, [0, 0])
	if counts[0] + counts[1] > 0:
		_swap_warning.text = tr("BESTIA_STORAGE_SWAP_ITEMS") % [leaving_name, counts[0], counts[1], leaving_name]
	else:
		_swap_warning.text = ""
	_swap_where.text = tr("BESTIA_STORAGE_SWAP_WHERE") % [_name_of(stored), leaving_name, leaving_name, _REST_MINUTES]


func _pick_swap_out(entity_id: int) -> void:
	_swap_out_entity_id = entity_id
	_render_swap()


func _on_summon_pressed() -> void:
	if _picked_id != 0 and ConnectionManager.is_ready_to_send():
		ConnectionManager.summon_stored_bestia(_picked_id)


func _on_swap_pressed() -> void:
	_swap_out_entity_id = 0
	_swap_box.visible = true
	_render_swap()


func _close_swap() -> void:
	_swap_out_entity_id = 0
	_swap_box.visible = false


func _on_swap_confirmed() -> void:
	if _picked_id == 0 or _swap_out_entity_id == 0 or not ConnectionManager.is_ready_to_send():
		return
	ConnectionManager.swap_stored_bestia(_picked_id, _swap_out_entity_id)
	_close_swap()


func _on_release_pressed() -> void:
	if _release.text != tr("BESTIA_STORAGE_RELEASE_CONFIRM"):
		_release.text = tr("BESTIA_STORAGE_RELEASE_CONFIRM")
		get_tree().create_timer(_RELEASE_CONFIRM_SECONDS).timeout.connect(
			func() -> void: _release.text = tr("BESTIA_STORAGE_RELEASE")
		)
		return
	_release.text = tr("BESTIA_STORAGE_RELEASE")
	if _picked_id != 0 and ConnectionManager.is_ready_to_send():
		ConnectionManager.release_stored_bestia(_picked_id)


## The server sends the rest left once; the window counts it down itself.
func _count_down_rest() -> void:
	if _storage == null:
		return

	var any_resting := false
	for stored in _storage.Bestias:
		if stored.RestingSeconds > 0:
			stored.RestingSeconds -= 1
			any_resting = true
	if any_resting and is_visible_in_tree():
		_render_list()
		_render_detail()


func _picked() -> StoredBestia:
	if _storage == null:
		return null
	for stored in _storage.Bestias:
		if int(stored.Info.PlayerBestiaId) == _picked_id:
			return stored
	return null


func _stored_ids() -> Array[int]:
	var ids: Array[int] = []
	if _storage != null:
		for stored in _storage.Bestias:
			ids.append(int(stored.Info.PlayerBestiaId))
	return ids


func _owned_bestia(entity_id: int) -> BestiaInfo:
	for bestia in _owned_bestias():
		if int(bestia.EntityId) == entity_id:
			return bestia
	return null


func _owned_bestias() -> Array[BestiaInfo]:
	var entity_manager := EntityManager.get_instance()
	return entity_manager.get_owned_bestias() if entity_manager != null else [] as Array[BestiaInfo]


func _name_of(stored: StoredBestia) -> String:
	return OwnedSlots.name_of(stored.Info)


func _species_line(stored: StoredBestia) -> String:
	return "%s · %s" % [BreedingText.species_name(stored.Info.MobId), tr("BESTIA_STORAGE_LEVEL") % stored.Info.Level]


func _rest_text(seconds: int) -> String:
	return tr("BESTIA_STORAGE_RESTS") % ceili(seconds / 60.0)
