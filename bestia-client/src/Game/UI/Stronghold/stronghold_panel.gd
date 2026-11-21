class_name StrongholdPanel
extends PanelContainer

## The Stronghold window at a guild's own Empyrite: its health, the three tracks members invest in, one step per
## click, its steward and its watchers. Opened by the server when a member clicks the Empyrite in reach, closed by walking away,
## like the city sign. Built in code.

signal stronghold_opened()
signal stronghold_closed()

const _WALK_AWAY_METRES := 8.0

## Mirrors net.bestia.zone.stronghold.EmpyriteInteraction.INVEST.
const _INVEST := "invest"
const _HIRE_STEWARD := "hire_steward"
const _ADD_WATCHER := "add_watcher"
const _REMOVE_WATCHER := "remove_watcher"
const _BRING_BACK_WATCHER := "bring_back_watcher"

## The proto's WatcherKind, in its order, by the server's name.
const _WATCHER_KINDS: Array[String] = ["MELEE", "ARCHER"]

## The proto's StrongholdTrack, in its order, by the server's name.
const _TRACK_NAMES: Array[String] = ["DEFENSE", "STORAGE", "ECONOMY"]

var _empyrite_entity_id: int = 0
var _opened_at := Vector3.ZERO

var _title: Label
var _hp_bar: ProgressBar
var _hp_text: Label
var _development: Label
var _defense_effect: Label
## One entry per track: level label, pips row, next-step label, invest button.
var _rows: Array[Dictionary] = []
var _steward_state: Label
var _steward_cost: Label
var _hire: Button
var _watcher_head: Label
var _watcher_state: Label
## One entry per watcher kind: count label, remove and add buttons.
var _watcher_rows: Array[Dictionary] = []
var _fallen: Label
var _bring_back_cost: Label
var _bring_back: Button
## The fallen watchers as last sent, and when, so the countdown runs without the server.
var _fallen_watchers: Array[Dictionary] = []
var _fallen_received_ms := 0


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(380, 0)
	_build()

	ConnectionManager.stronghold_opened.connect(_on_opened)
	visibility_changed.connect(func() -> void: if not visible: close_stronghold())
	set_process(false)


func close_stronghold() -> void:
	if _empyrite_entity_id == 0:
		return
	_empyrite_entity_id = 0
	set_process(false)
	stronghold_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_stronghold()
		return
	_show_fallen()


func _on_opened(msg: StrongholdSMSG) -> void:
	var was_open := _empyrite_entity_id != 0
	_empyrite_entity_id = msg.EmpyriteEntityId
	if not was_open:
		var player := _player()
		_opened_at = player.global_position if player != null else Vector3.ZERO
		set_process(player != null)

	_title.text = tr("STRONGHOLD_TITLE") % [msg.SeedName, msg.GuildName]
	UiStyle.set_bar(_hp_bar, msg.Hp, msg.MaxHp)
	_hp_text.text = "%s / %s" % [UiStyle.group_digits(msg.Hp), UiStyle.group_digits(msg.MaxHp)]
	_development.visible = msg.HasPermanentDevelopment
	_defense_effect.text = tr("STRONGHOLD_EFFECT_DEFENSE") % [UiStyle.group_digits(msg.HpPerDefense), msg.DefPerDefense]

	for info in msg.Tracks:
		var row: Dictionary = _rows[int(info["track"])]
		var level: int = info["level"]
		var maxed := level >= msg.MaxLevel
		(row["level"] as Label).text = tr("STRONGHOLD_LEVEL") % level
		_show_pips(row["pips"] as HBoxContainer, msg.StepsPerLevel if maxed else int(info["steps"]), msg.StepsPerLevel)
		var next_coins := UiStyle.group_digits(int(info["next_step_coins"]))
		(row["next"] as Label).text = tr("STRONGHOLD_MAXED") if maxed else tr("STRONGHOLD_NEXT_STEP") % next_coins
		(row["invest"] as Button).disabled = maxed

	_show_steward(msg)
	_show_watchers(msg)
	stronghold_opened.emit()


func _invest(track: int) -> void:
	if _empyrite_entity_id == 0:
		return
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, _INVEST)
	args.SetText(ScriptArgKeys.TRACK, _TRACK_NAMES[track])
	ConnectionManager.interact_entity(_empyrite_entity_id, args)


func _hire_steward() -> void:
	if _empyrite_entity_id == 0:
		return
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, _HIRE_STEWARD)
	ConnectionManager.interact_entity(_empyrite_entity_id, args)


func _show_steward(msg: StrongholdSMSG) -> void:
	var may_hire := msg.HasStewardContract and not msg.HasSteward
	if msg.HasSteward:
		_steward_state.text = tr("STRONGHOLD_STEWARD_SERVING")
	elif msg.HasStewardContract:
		_steward_state.text = tr("STRONGHOLD_STEWARD_NONE")
	else:
		_steward_state.text = tr("STRONGHOLD_STEWARD_NEEDS_CONTRACT")
	_steward_cost.text = tr("STRONGHOLD_STEWARD_COST") % UiStyle.group_digits(msg.StewardCoins)
	_steward_cost.visible = may_hire
	_hire.visible = may_hire


func _order_watcher(action: String, kind: int) -> void:
	if _empyrite_entity_id == 0:
		return
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, action)
	if kind >= 0:
		args.SetText(ScriptArgKeys.WATCHER, _WATCHER_KINDS[kind])
	ConnectionManager.interact_entity(_empyrite_entity_id, args)


func _show_watchers(msg: StrongholdSMSG) -> void:
	var counts := [msg.MeleeWatchers, msg.ArcherWatchers]
	var total: int = counts[0] + counts[1]
	_watcher_head.text = tr("STRONGHOLD_WATCHERS_HEAD") % [msg.WatcherLevel, total, msg.WatcherPosts]
	_watcher_head.visible = msg.HasWatcherResearch
	_watcher_state.text = tr("STRONGHOLD_WATCHERS_NEED_RESEARCH")
	_watcher_state.visible = not msg.HasWatcherResearch

	for kind in _watcher_rows.size():
		var row: Dictionary = _watcher_rows[kind]
		(row["line"] as Control).visible = msg.HasWatcherResearch
		(row["count"] as Label).text = str(counts[kind])
		(row["remove"] as Button).visible = msg.MaySetWatchers
		(row["remove"] as Button).disabled = counts[kind] <= 0
		(row["add"] as Button).visible = msg.MaySetWatchers
		(row["add"] as Button).disabled = total >= msg.WatcherPosts

	_fallen_watchers.assign(msg.FallenWatchers)
	_fallen_received_ms = Time.get_ticks_msec()
	_bring_back_cost.text = tr("STRONGHOLD_WATCHERS_BRING_BACK_COST") % UiStyle.group_digits(msg.BringBackCoins)
	_show_fallen()


## "Fallen: 1 Melee, 1 Archer · next back in 12:03", counted down here between messages.
func _show_fallen() -> void:
	var down := not _fallen_watchers.is_empty()
	_fallen.visible = down
	_bring_back_cost.visible = down
	_bring_back.visible = down
	if not down:
		return

	var per_kind := [0, 0]
	for fallen in _fallen_watchers:
		per_kind[int(fallen["kind"])] += 1
	var parts: Array[String] = []
	for kind in per_kind.size():
		if per_kind[kind] > 0:
			parts.append("%d %s" % [per_kind[kind], tr("STRONGHOLD_WATCHER_SHORT_%s" % _WATCHER_KINDS[kind])])

	var elapsed := floori((Time.get_ticks_msec() - _fallen_received_ms) / 1000.0)
	var seconds_left := maxi(0, int(_fallen_watchers[0]["seconds_left"]) - elapsed)
	var clock := "%d:%02d" % [floori(seconds_left / 60.0), seconds_left % 60]
	_fallen.text = tr("STRONGHOLD_WATCHERS_FALLEN") % [", ".join(parts), clock]


func _show_pips(pips: HBoxContainer, filled: int, total: int) -> void:
	while pips.get_child_count() < total:
		pips.add_child(UiStyle.dot(UiStyle.BAR_BG))
	for i in pips.get_child_count():
		var pip := pips.get_child(i) as Panel
		pip.visible = i < total
		UiStyle.set_dot(pip, UiStyle.ACCENT if i < filled else UiStyle.BAR_BG)


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


func _build() -> void:
	var body := UiStyle.vbox(10)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_title = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_title)

	var hp_row := UiStyle.hbox(8)
	body.add_child(hp_row)
	hp_row.add_child(UiStyle.label(tr("STRONGHOLD_EMPYRITE"), 13))
	_hp_bar = UiStyle.bar(UiStyle.HP_FILL, 10)
	_hp_bar.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_hp_bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	hp_row.add_child(_hp_bar)
	_hp_text = UiStyle.label("", 12, UiStyle.MUTED)
	hp_row.add_child(_hp_text)

	for track in _TRACK_NAMES.size():
		body.add_child(_build_track_row(track))

	body.add_child(HSeparator.new())
	body.add_child(_build_steward_row())
	body.add_child(HSeparator.new())
	body.add_child(_build_watcher_rows())

	_development = UiStyle.label(tr("STRONGHOLD_PERMANENT_DEVELOPMENT"), 12, UiStyle.GUILD)
	_development.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.add_child(_development)


func _build_track_row(track: int) -> Control:
	var rows := UiStyle.vbox(2)

	var head := UiStyle.hbox(8)
	rows.add_child(head)
	head.add_child(UiStyle.label(tr("STRONGHOLD_TRACK_%s" % _TRACK_NAMES[track]), 14))
	var level := UiStyle.label("", 13, UiStyle.MUTED)
	head.add_child(level)
	var pips := UiStyle.hbox(3)
	pips.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	head.add_child(pips)

	var effect := UiStyle.label(tr("STRONGHOLD_EFFECT_%s" % _TRACK_NAMES[track]), 12, UiStyle.MUTED)
	rows.add_child(UiStyle.margin(effect, 12, 0, 0, 0))
	if _TRACK_NAMES[track] == "DEFENSE":
		_defense_effect = effect

	var foot := UiStyle.hbox(8)
	rows.add_child(UiStyle.margin(foot, 12, 0, 0, 0))
	var next := UiStyle.label("", 12)
	next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	foot.add_child(next)
	var invest := Button.new()
	invest.text = tr("STRONGHOLD_INVEST")
	invest.pressed.connect(func() -> void: _invest(track))
	foot.add_child(invest)

	_rows.append({"level": level, "pips": pips, "next": next, "invest": invest})
	return rows


func _build_steward_row() -> Control:
	var rows := UiStyle.vbox(2)
	rows.add_child(UiStyle.label(tr("STRONGHOLD_STEWARD"), 14))

	_steward_state = UiStyle.label("", 12, UiStyle.MUTED)
	_steward_state.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(UiStyle.margin(_steward_state, 12, 0, 0, 0))

	var foot := UiStyle.hbox(8)
	rows.add_child(UiStyle.margin(foot, 12, 0, 0, 0))
	_steward_cost = UiStyle.label("", 12)
	_steward_cost.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	foot.add_child(_steward_cost)
	_hire = Button.new()
	_hire.text = tr("STRONGHOLD_HIRE")
	_hire.pressed.connect(_hire_steward)
	foot.add_child(_hire)

	return rows


func _build_watcher_rows() -> Control:
	var rows := UiStyle.vbox(2)

	var head := UiStyle.hbox(8)
	rows.add_child(head)
	head.add_child(UiStyle.label(tr("STRONGHOLD_WATCHERS"), 14))
	_watcher_head = UiStyle.label("", 13, UiStyle.MUTED)
	head.add_child(_watcher_head)

	_watcher_state = UiStyle.label("", 12, UiStyle.MUTED)
	_watcher_state.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(UiStyle.margin(_watcher_state, 12, 0, 0, 0))

	for kind in _WATCHER_KINDS.size():
		var line := UiStyle.hbox(8)
		rows.add_child(UiStyle.margin(line, 12, 0, 0, 0))
		var kind_name := UiStyle.label(tr("STRONGHOLD_WATCHER_%s" % _WATCHER_KINDS[kind]), 12)
		kind_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		line.add_child(kind_name)
		var count := UiStyle.label("", 12)
		count.custom_minimum_size = Vector2(24, 0)
		line.add_child(count)
		var remove := Button.new()
		remove.text = "−"
		remove.pressed.connect(func() -> void: _order_watcher(_REMOVE_WATCHER, kind))
		line.add_child(remove)
		var add := Button.new()
		add.text = "+"
		add.pressed.connect(func() -> void: _order_watcher(_ADD_WATCHER, kind))
		line.add_child(add)
		_watcher_rows.append({"line": line.get_parent(), "count": count, "remove": remove, "add": add})

	_fallen = UiStyle.label("", 12, UiStyle.MUTED)
	_fallen.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(UiStyle.margin(_fallen, 12, 0, 0, 0))

	var foot := UiStyle.hbox(8)
	rows.add_child(UiStyle.margin(foot, 12, 0, 0, 0))
	_bring_back_cost = UiStyle.label("", 12)
	_bring_back_cost.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	foot.add_child(_bring_back_cost)
	_bring_back = Button.new()
	_bring_back.text = tr("STRONGHOLD_WATCHERS_BRING_BACK")
	_bring_back.pressed.connect(func() -> void: _order_watcher(_BRING_BACK_WATCHER, -1))
	foot.add_child(_bring_back)

	return rows
