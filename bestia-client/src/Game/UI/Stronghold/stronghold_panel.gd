class_name StrongholdPanel
extends PanelContainer

## The Stronghold window at a guild's own Empyrite: its health, and the three tracks members invest in, one step
## per click. Opened by the server when a member clicks the Empyrite in reach, closed by walking away, like the
## city sign. Built in code.

signal stronghold_opened()
signal stronghold_closed()

const _WALK_AWAY_METRES := 8.0

## Mirrors net.bestia.zone.stronghold.EmpyriteInteraction.INVEST.
const _INVEST := "invest"

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

	stronghold_opened.emit()


func _invest(track: int) -> void:
	if _empyrite_entity_id == 0:
		return
	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetText(ScriptArgKeys.ACTION, _INVEST)
	args.SetText(ScriptArgKeys.TRACK, _TRACK_NAMES[track])
	ConnectionManager.interact_entity(_empyrite_entity_id, args)


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
