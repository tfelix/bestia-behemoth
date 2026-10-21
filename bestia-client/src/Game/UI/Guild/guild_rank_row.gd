class_name GuildRankRow
extends PanelContainer

## One rank in the guild window: its name, the tax it pays and what it may do. Edited in place and sent with
## Save; the guild that comes back is what the row then shows.
##
## A row with unsaved edits keeps them when the guild is pushed again - EXP flushes push it every half minute,
## and losing a half-typed name to one would make the window unusable.

signal save_requested(rank_id: int, rank_name: String, tax_percent: int, can_invite: bool, can_kick: bool, can_manage_ranks: bool)
signal delete_requested(rank_id: int)

var rank_id: int = 0

var _name: LineEdit
var _tax: SpinBox
var _invite: CheckBox
var _kick: CheckBox
var _manage: CheckBox
var _save: Button
var _delete: Button
var _dirty := false
var _drawing := false


func _init() -> void:
	var line := UiStyle.hbox(6)
	add_child(line)

	_name = LineEdit.new()
	_name.max_length = 20
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_name.text_changed.connect(func(_text: String) -> void: _mark_dirty())
	line.add_child(_name)

	_tax = SpinBox.new()
	_tax.suffix = "%"
	_tax.custom_minimum_size = Vector2(76, 0)
	_tax.tooltip_text = tr("GUILD_COL_TAX")
	_tax.value_changed.connect(func(_value: float) -> void: _mark_dirty())
	line.add_child(_tax)

	_invite = _permission(tr("GUILD_PERM_INVITE"))
	_kick = _permission(tr("GUILD_PERM_KICK"))
	_manage = _permission(tr("GUILD_PERM_MANAGE_RANKS"))
	line.add_child(_invite)
	line.add_child(_kick)
	line.add_child(_manage)

	_save = Button.new()
	_save.text = tr("GUILD_RANK_SAVE")
	_save.theme_type_variation = &"ChipButton"
	_save.disabled = true
	_save.pressed.connect(_on_save)
	line.add_child(_save)

	_delete = UiStyle.glyph_button("✕", tr("GUILD_RANK_DELETE"))
	_delete.pressed.connect(func() -> void: delete_requested.emit(rank_id))
	line.add_child(_delete)


func _permission(tooltip: String) -> CheckBox:
	var box := CheckBox.new()
	box.tooltip_text = tooltip
	box.toggled.connect(func(_on: bool) -> void: _mark_dirty())
	return box


## [param editable] when the viewer may change this rank; [param deletable] when they may also take it away.
func show_rank(rank: GuildRankInfo, editable: bool, deletable: bool, min_tax: int, max_tax: int) -> void:
	rank_id = rank.RankId
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 4, 6))

	# Setting the bounds can move the value, and a moved value must not read as the player's edit.
	_drawing = true
	var is_leader_rank := rank.Position == 0
	_name.editable = editable
	_tax.editable = editable
	_tax.min_value = min_tax
	_tax.max_value = max_tax
	# The leader's rank keeps every permission, whatever is sent; only its name and tax are the leader's to set.
	for box in [_invite, _kick, _manage]:
		box.disabled = not editable or is_leader_rank
		if is_leader_rank:
			box.tooltip_text = tr("GUILD_RANK_LEADER_LOCKED")
	_save.visible = editable
	_delete.visible = editable and not is_leader_rank
	_delete.disabled = not deletable

	if not _dirty:
		_name.text = rank.Name
		_tax.value = rank.TaxPercent
		_invite.button_pressed = rank.CanInvite
		_kick.button_pressed = rank.CanKick
		_manage.button_pressed = rank.CanManageRanks
		_save.disabled = true
	_drawing = false


func _mark_dirty() -> void:
	if _drawing:
		return
	_dirty = true
	_save.disabled = false


func _on_save() -> void:
	var rank_name := _name.text.strip_edges()
	if rank_name.is_empty():
		return

	# Cleared on send, so the guild that answers it is drawn over the row - accepted or refused.
	_dirty = false
	_save.disabled = true
	save_requested.emit(rank_id, rank_name, int(_tax.value), _invite.button_pressed, _kick.button_pressed, _manage.button_pressed)
