class_name BlackboardPanel
extends PanelContainer

## A town's quest board: every open quest, grouped as the played master sees it, with one quest's details beside
## the list. Every board shows the same list. Opened by the server when the player clicks a board, and closed by
## walking away, like the post office. Built in code, like [PostOfficePanel].

signal blackboard_opened()
signal blackboard_closed()

const _WALK_AWAY_METRES := 10.0

## How many item names the post form offers for what was typed.
const _MAX_NAME_MATCHES := 8

## The list's groups, in order, by the quest's role for the played master.
const _GROUPS := [["accepted", "BLACKBOARD_GROUP_SIGNED"], ["offered", "BLACKBOARD_GROUP_OPEN"], ["posted", "BLACKBOARD_GROUP_YOURS"]]

## Set by the UI, which owns the inventory window: the post form shows the gold in the bag.
var inventory: Inventory

## The board the window is open at. Zero while closed. Every request names it.
var _board_id: int = 0
var _opened_at := Vector3.ZERO
var _quests: Array = []
var _selected_id: int = 0
var _board: BlackboardSMSG
var _post_item_id: int = 0
var _post_days: int = 0

var _title: Label
var _notices: VBoxContainer
var _search: LineEdit
var _list: VBoxContainer
var _empty: Label
var _detail: VBoxContainer
var _detail_title: Label
var _detail_subtitle: Label
var _detail_progress: Label
var _detail_bar: ProgressBar
var _detail_reward: Label
var _withdraw: Button
var _sign: Button
var _hand_in: Button
var _drop: Button
var _carried: Label
var _post_button: Button
var _form: VBoxContainer
var _post_name: LineEdit
var _post_matches: ItemList
var _post_amount: SpinBox
var _post_coins: SpinBox
var _post_runs: HBoxContainer
var _post_pay: Label
var _post_fee: Label
var _post_total: Label
var _post_send: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(600, 400)
	_build()

	ConnectionManager.blackboard_opened.connect(_on_opened)
	ConnectionManager.quest_updated.connect(_on_quest_updated)
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func is_open() -> bool:
	return _board_id != 0


func close_board() -> void:
	if not is_open():
		return

	_board_id = 0
	set_process(false)
	blackboard_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_board()


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 10, 8, 10, 10))

	var header := UiStyle.hbox(8)
	body.add_child(header)

	_title = UiStyle.label("", 16, UiStyle.ACCENT)
	_title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(_title)

	_post_button = Button.new()
	_post_button.text = tr("BLACKBOARD_POST")
	_post_button.focus_mode = Control.FOCUS_NONE
	_post_button.pressed.connect(_show_form)
	header.add_child(_post_button)

	var columns := UiStyle.hbox(10)
	columns.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(columns)

	var left := UiStyle.vbox(6)
	left.custom_minimum_size = Vector2(250, 0)
	columns.add_child(left)

	_notices = UiStyle.vbox(2)
	left.add_child(_notices)

	_search = LineEdit.new()
	_search.placeholder_text = tr("BLACKBOARD_SEARCH")
	_search.clear_button_enabled = true
	_search.text_changed.connect(func(_text: String) -> void: _refresh_list())
	left.add_child(_search)

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	left.add_child(scroll)

	_list = UiStyle.vbox(3)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_list)

	_empty = UiStyle.label(tr("BLACKBOARD_EMPTY"), 12, UiStyle.MUTED)
	_empty.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	left.add_child(_empty)

	columns.add_child(VSeparator.new())
	_detail = _build_detail()
	columns.add_child(_detail)
	_form = _build_form()
	columns.add_child(_form)


func _build_detail() -> VBoxContainer:
	var detail := UiStyle.vbox(6)
	detail.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	_detail_title = UiStyle.label("", 15)
	_detail_title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	detail.add_child(_detail_title)

	_detail_subtitle = UiStyle.label("", 11, UiStyle.MUTED)
	_detail_subtitle.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	detail.add_child(_detail_subtitle)

	_detail_progress = UiStyle.label("", 12)
	detail.add_child(_detail_progress)

	_detail_bar = UiStyle.bar(UiStyle.EXP_FILL, 8)
	detail.add_child(_detail_bar)

	_detail_reward = UiStyle.label("", 12)
	detail.add_child(_detail_reward)

	_carried = UiStyle.label("", 12, UiStyle.MUTED)
	detail.add_child(_carried)

	var actions := UiStyle.hbox(6)
	detail.add_child(actions)

	_sign = Button.new()
	_sign.text = tr("BLACKBOARD_SIGN")
	_sign.tooltip_text = tr("BLACKBOARD_SIGN_HINT")
	_sign.pressed.connect(func() -> void: _send_for_selected("sign"))
	actions.add_child(_sign)

	_hand_in = Button.new()
	_hand_in.pressed.connect(_on_hand_in_pressed)
	actions.add_child(_hand_in)

	_drop = Button.new()
	_drop.text = tr("BLACKBOARD_DROP")
	_drop.pressed.connect(func() -> void: ConnectionManager.abandon_quest(_selected_id))
	actions.add_child(_drop)

	_withdraw = Button.new()
	_withdraw.text = tr("BLACKBOARD_WITHDRAW")
	_withdraw.tooltip_text = tr("BLACKBOARD_WITHDRAW_HINT")
	_withdraw.pressed.connect(func() -> void: _send_for_selected("withdraw"))
	actions.add_child(_withdraw)

	detail.visible = false
	return detail


func _build_form() -> VBoxContainer:
	var form := UiStyle.vbox(6)
	form.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	form.add_child(UiStyle.label(tr("BLACKBOARD_POST"), 15))

	var fields := GridContainer.new()
	fields.columns = 2
	fields.add_theme_constant_override("h_separation", 8)
	form.add_child(fields)

	fields.add_child(UiStyle.label(tr("BLACKBOARD_POST_ITEM"), 12, UiStyle.MUTED))
	# One cell for the name and its matches: a grid would shift every later row while the matches are hidden.
	var item_cell := UiStyle.vbox(2)
	item_cell.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	fields.add_child(item_cell)

	_post_name = LineEdit.new()
	_post_name.placeholder_text = tr("BLACKBOARD_POST_ITEM_HINT")
	_post_name.text_changed.connect(_on_post_name_changed)
	item_cell.add_child(_post_name)

	_post_matches = ItemList.new()
	_post_matches.custom_minimum_size = Vector2(0, 70)
	_post_matches.visible = false
	_post_matches.item_selected.connect(_on_post_match_selected)
	item_cell.add_child(_post_matches)

	fields.add_child(UiStyle.label(tr("BLACKBOARD_POST_AMOUNT"), 12, UiStyle.MUTED))
	_post_amount = _spin_box()
	fields.add_child(_post_amount)

	fields.add_child(UiStyle.label(tr("BLACKBOARD_POST_COINS"), 12, UiStyle.MUTED))
	_post_coins = _spin_box()
	fields.add_child(_post_coins)

	fields.add_child(UiStyle.label(tr("BLACKBOARD_POST_RUNS"), 12, UiStyle.MUTED))
	_post_runs = UiStyle.hbox(4)
	fields.add_child(_post_runs)

	form.add_child(HSeparator.new())
	_post_pay = UiStyle.label("", 12)
	form.add_child(_post_pay)
	_post_fee = UiStyle.label("", 12)
	form.add_child(_post_fee)
	_post_total = UiStyle.label("", 13, UiStyle.ACCENT)
	form.add_child(_post_total)

	var buttons := UiStyle.hbox(6)
	form.add_child(buttons)
	_post_send = Button.new()
	_post_send.pressed.connect(_on_post_pressed)
	buttons.add_child(_post_send)
	var cancel := Button.new()
	cancel.text = tr("BLACKBOARD_POST_CANCEL")
	cancel.pressed.connect(_hide_form)
	buttons.add_child(cancel)

	form.visible = false
	return form


func _spin_box() -> SpinBox:
	var box := SpinBox.new()
	box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	box.min_value = 1
	box.value = 1
	box.rounded = true
	box.value_changed.connect(func(_value: float) -> void: _update_cost())
	return box


func _on_opened(msg: BlackboardSMSG) -> void:
	var was_open := is_open()
	_board_id = msg.BoardEntityId

	var player := _player()
	if not was_open:
		_opened_at = player.global_position if player != null else Vector3.ZERO
		_selected_id = 0
	set_process(player != null)

	_title.text = tr("BLACKBOARD_TITLE") % msg.PlaceName if not msg.PlaceName.is_empty() else tr("BLACKBOARD_TITLE_NOWHERE")
	_board = msg
	_quests = msg.Quests
	_post_button.disabled = msg.OpenPostings >= msg.MaxPostings
	_post_button.tooltip_text = tr("BLACKBOARD_POST_LIMIT") % msg.MaxPostings if _post_button.disabled else ""
	_post_amount.max_value = msg.MaxAmount
	_post_coins.max_value = msg.MaxCoinsEach
	_build_runs(msg.Runs)
	_show_notices(msg.Notices, player)
	if not was_open:
		_hide_form()
	_refresh_list()

	if not was_open:
		blackboard_opened.emit()


## The world events of the province, first: they are what the town talks about. Nobody signs them.
func _show_notices(notices: Array, player: Entity) -> void:
	for child in _notices.get_children():
		child.queue_free()
	if notices.is_empty():
		return

	_notices.add_child(UiStyle.label(tr("BLACKBOARD_GROUP_EVENTS"), 11, UiStyle.MUTED))
	for notice in notices:
		var line := UiStyle.label(_notice_text(notice, player), 12, UiStyle.ACCENT)
		line.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		line.tooltip_text = tr("BLACKBOARD_NOTICE_HINT")
		line.mouse_filter = Control.MOUSE_FILTER_PASS
		_notices.add_child(line)
	_notices.add_child(HSeparator.new())


func _notice_text(notice: BlackboardNoticeEntry, player: Entity) -> String:
	var name := tr("WORLD_EVENT_" + notice.EventId.to_upper().replace("-", "_"))
	var text := tr("BLACKBOARD_NOTICE") % [name.left(1).to_upper() + name.substr(1), notice.PlaceName, tr("SENSE_EVENT_STAGE_%d" % notice.Stage)]
	if player == null:
		return text

	var here := Vector3(player.global_position.x, 0.0, player.global_position.z)
	var there := Vector3(float(notice.X), 0.0, float(notice.Y))
	var bearing := CompassStrip.bearing_between(here, there)
	return "%s · %s %.1fkm" % [text, CompassStrip.name_of(bearing), here.distance_to(there) / 1000.0]


func _refresh_list() -> void:
	for child in _list.get_children():
		child.queue_free()

	var query := _search.text.strip_edges().to_lower()
	var shown := _quests.filter(func(quest) -> bool: return query.is_empty() or QuestStyle.title_of(quest).to_lower().contains(query))

	for group in _GROUPS:
		var members := shown.filter(func(quest) -> bool: return _group_of(quest) == group[0])
		if members.is_empty():
			continue
		_list.add_child(UiStyle.label(tr(group[1]), 11, UiStyle.MUTED))
		for quest in members:
			_list.add_child(_row(quest))

	_empty.visible = shown.is_empty()
	var selected = shown.filter(func(quest) -> bool: return quest.QuestId == _selected_id)
	_show(selected[0] if not selected.is_empty() else (shown[0] if not shown.is_empty() else null))


func _row(quest) -> Button:
	var row := Button.new()
	row.flat = true
	row.alignment = HORIZONTAL_ALIGNMENT_LEFT
	row.focus_mode = Control.FOCUS_NONE
	row.text = "[%d] %s" % [quest.SuggestedLevel, QuestStyle.title_of(quest)]
	row.add_theme_color_override("font_color", QuestStyle.level_colour(quest.SuggestedLevel))
	row.pressed.connect(func() -> void: _show(quest))
	return row


func _show(quest) -> void:
	if quest != null:
		_form.visible = false
	_detail.visible = quest != null and not _form.visible
	if quest == null:
		_selected_id = 0
		return

	_selected_id = quest.QuestId
	_detail_title.text = QuestStyle.title_of(quest)
	_detail_subtitle.text = tr("BLACKBOARD_SUBTITLE") % [quest.PosterName, QuestStyle.time_left(quest), quest.SuggestedLevel]

	var objective = quest.Objectives[0] if not quest.Objectives.is_empty() else null
	_detail_progress.visible = objective != null
	_detail_bar.visible = objective != null
	if objective != null:
		if quest.RoleName == "accepted":
			_detail_progress.text = tr("BLACKBOARD_PROGRESS_YOURS") % [objective.Progress, objective.Required, objective.Yours]
		else:
			_detail_progress.text = tr("BLACKBOARD_PROGRESS") % [objective.Progress, objective.Required]
		UiStyle.set_bar(_detail_bar, objective.Progress, objective.Required)

	var reward = quest.Rewards[0] if not quest.Rewards.is_empty() else null
	_detail_reward.visible = reward != null
	if reward != null:
		_detail_reward.text = tr("BLACKBOARD_PAYS_EACH") % reward.Amount

	var takes_work: bool = quest.StatusName == "open" or quest.StatusName == "closing"
	var signed: bool = quest.RoleName == "accepted"
	_withdraw.visible = quest.RoleName == "posted" and quest.StatusName == "open"
	_sign.visible = quest.RoleName == "offered" and quest.StatusName == "open"
	_drop.visible = signed
	_hand_in.visible = signed and takes_work and _hand_in_amount(quest) > 0
	_hand_in.text = tr("BLACKBOARD_HAND_IN") % _hand_in_amount(quest)
	_carried.visible = signed and takes_work
	_carried.text = tr("BLACKBOARD_CARRIED") % _carried_amount(quest)
	if quest.StatusName == "closing":
		_detail_subtitle.text += " · " + tr("BLACKBOARD_CLOSING")


## What the played master carries of what the quest wants.
func _carried_amount(quest) -> int:
	if inventory == null or quest.Objectives.is_empty():
		return 0
	return inventory.get_item_count(quest.Objectives[0].ItemId)


## As much as the master carries, but never more than the quest still wants.
func _hand_in_amount(quest) -> int:
	if quest.Objectives.is_empty():
		return 0
	var objective = quest.Objectives[0]
	return mini(_carried_amount(quest), objective.Required - objective.Progress)


func _selected_quest():
	for quest in _quests:
		if quest.QuestId == _selected_id:
			return quest
	return null


func _on_hand_in_pressed() -> void:
	var quest = _selected_quest()
	if not is_open() or quest == null:
		return

	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, "handIn")
	args.SetInt(ScriptArgKeys.QUEST_ID, _selected_id)
	args.SetInt(ScriptArgKeys.AMOUNT, _hand_in_amount(quest))
	ConnectionManager.interact_entity(_board_id, args)


func _send_for_selected(action: String) -> void:
	if not is_open() or _selected_id == 0:
		return

	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, action)
	args.SetInt(ScriptArgKeys.QUEST_ID, _selected_id)
	ConnectionManager.interact_entity(_board_id, args)


## A quest on the board changed - another signer's hand-in, a drop, a deadline - so the board asks again.
func _on_quest_updated(_msg: QuestUpdatedSMSG) -> void:
	if is_open():
		ConnectionManager.interact_entity(_board_id)


# ----------------------------------------------------------------- posting

func _show_form() -> void:
	_detail.visible = false
	_form.visible = true
	_post_item_id = 0
	_post_name.text = ""
	_post_matches.visible = false
	_update_cost()


func _hide_form() -> void:
	_form.visible = false
	_detail.visible = _selected_id != 0


func _build_runs(runs: Array) -> void:
	for child in _post_runs.get_children():
		child.queue_free()

	var group := ButtonGroup.new()
	for run in runs:
		var chip := Button.new()
		chip.toggle_mode = true
		chip.button_group = group
		chip.focus_mode = Control.FOCUS_NONE
		chip.text = tr("BLACKBOARD_POST_RUN") % [run.Days, run.FeePercent]
		chip.toggled.connect(func(on: bool) -> void:
			if on:
				_post_days = run.Days
				_update_cost())
		_post_runs.add_child(chip)

	# The middle run is the usual pick; any is fine as long as one is.
	if not runs.is_empty():
		var middle := floori(runs.size() / 2.0)
		(_post_runs.get_child(middle) as Button).button_pressed = true
		_post_days = runs[middle].Days


func _on_post_name_changed(text: String) -> void:
	_post_item_id = 0
	_post_matches.clear()
	var needle := text.strip_edges().to_lower()
	if not needle.is_empty():
		for item in ItemDB.get_instance().all_items():
			if tr(item.name_key).to_lower().contains(needle):
				_post_matches.add_item(tr(item.name_key), item.get_icon())
				_post_matches.set_item_metadata(_post_matches.item_count - 1, item.item_id)
				if _post_matches.item_count >= _MAX_NAME_MATCHES:
					break
	_post_matches.visible = _post_matches.item_count > 0
	_update_cost()


func _on_post_match_selected(index: int) -> void:
	_post_item_id = _post_matches.get_item_metadata(index)
	_post_name.set_block_signals(true)
	_post_name.text = _post_matches.get_item_text(index)
	_post_name.set_block_signals(false)
	_post_matches.visible = false
	_update_cost()


func _fee_percent() -> int:
	if _board == null:
		return 0
	for run in _board.Runs:
		if run.Days == _post_days:
			return run.FeePercent
	return 0


func _update_cost() -> void:
	var pay := int(_post_amount.value) * int(_post_coins.value)
	var fee := QuestStyle.board_fee(pay, _fee_percent())
	var held := inventory.gold() if inventory != null else 0

	_post_pay.text = tr("BLACKBOARD_POST_PAY") % UiStyle.group_digits(pay)
	_post_fee.text = tr("BLACKBOARD_POST_FEE") % UiStyle.group_digits(fee)
	_post_total.text = tr("BLACKBOARD_POST_TOTAL") % [UiStyle.group_digits(pay + fee), UiStyle.group_digits(held)]
	_post_send.text = tr("BLACKBOARD_POST_SEND") % UiStyle.group_digits(pay + fee)
	_post_send.disabled = _post_item_id == 0 or _post_days == 0 or pay + fee > held


func _on_post_pressed() -> void:
	if not is_open() or _post_item_id == 0:
		return

	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, "post")
	args.SetInt(ScriptArgKeys.ITEM_ID, _post_item_id)
	args.SetInt(ScriptArgKeys.AMOUNT, int(_post_amount.value))
	args.SetInt(ScriptArgKeys.COINS_EACH, int(_post_coins.value))
	args.SetInt(ScriptArgKeys.DAYS, _post_days)
	ConnectionManager.interact_entity(_board_id, args)
	_hide_form()




func _group_of(quest) -> String:
	if quest.RoleName == "accepted" or quest.RoleName == "posted":
		return quest.RoleName
	return "offered"


## The window's own close button only hides it.
func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_board()


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null
