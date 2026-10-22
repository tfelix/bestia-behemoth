class_name GuildSkillsPage
extends HBoxContainer

## The guild window's Skills tab: the tree, and beside it what the picked skill does, what it needs and, for
## the leader, the button that spends a point on it.

## Skills whose effect lives in a Guild Stronghold, which does not exist yet.
const NEEDS_STRONGHOLD: Array[int] = [9002, 9003, 9004, 9005, 9006, 9007, 9020]
## Learnable now, but what it unlocks comes with a later update.
const EFFECT_LATER: Array[int] = [9001]

var _info: GuildInfoSMSG
var _is_leader := false

var _points: Label
var _tree: GuildSkillTree
var _name: Label
var _level: Label
var _description: RichTextLabel
var _requirements: RichTextLabel
var _note: Label
var _invest: Button


func _init() -> void:
	add_theme_constant_override("separation", 8)

	var scroll := ScrollContainer.new()
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_tree = GuildSkillTree.new()
	_tree.skill_selected.connect(func(_skill_id: int) -> void: _show_selected())
	scroll.add_child(UiStyle.margin(_tree, 2, 2, 2, 2))
	add_child(scroll)

	add_child(_build_details())
	_show_selected()


func _build_details() -> Control:
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.ROW_BG, 6, 10))
	card.custom_minimum_size = Vector2(210, 0)
	var rows := UiStyle.vbox(6)
	card.add_child(rows)

	var points := UiStyle.hbox(6)
	points.add_child(UiStyle.label(tr("GUILD_SKILL_POINTS"), 12, UiStyle.MUTED))
	_points = UiStyle.label("", 12, UiStyle.ACCENT)
	points.add_child(_points)
	rows.add_child(points)
	rows.add_child(HSeparator.new())

	_name = UiStyle.label("", 14, UiStyle.ACCENT)
	_name.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(_name)
	_level = UiStyle.label("", 12, UiStyle.MUTED)
	rows.add_child(_level)

	_description = _rich_text()
	rows.add_child(_description)
	_requirements = _rich_text()
	rows.add_child(_requirements)
	_note = UiStyle.label("", 11, UiStyle.DANGER)
	_note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rows.add_child(_note)

	var push_down := Control.new()
	push_down.size_flags_vertical = Control.SIZE_EXPAND_FILL
	rows.add_child(push_down)
	_invest = Button.new()
	_invest.text = tr("GUILD_SKILL_LEARN")
	_invest.theme_type_variation = &"PrimaryButton"
	_invest.pressed.connect(func() -> void: ConnectionManager.invest_guild_skill(_tree.selected_skill_id))
	rows.add_child(_invest)
	return card


func _rich_text() -> RichTextLabel:
	var text := RichTextLabel.new()
	text.bbcode_enabled = true
	text.fit_content = true
	text.scroll_active = false
	text.add_theme_font_size_override("normal_font_size", 12)
	text.add_theme_color_override("default_color", UiStyle.MUTED)
	return text


func show_skills(info: GuildInfoSMSG, is_leader: bool) -> void:
	_info = info
	_is_leader = is_leader
	_points.text = str(info.SkillPoints)
	_tree.show_tree(info.Skills, _may_raise)
	_show_selected()


func _may_raise(skill: GuildSkillInfo) -> bool:
	return _is_leader and _info.SkillPoints > 0 and skill.Level < skill.MaxLevel and _tree.prerequisites_met(skill)


func _show_selected() -> void:
	var skill := _selected_skill()
	_description.visible = skill != null
	_requirements.visible = skill != null
	_level.visible = skill != null
	_invest.visible = skill != null and _is_leader
	if skill == null:
		_name.text = tr("GUILD_SKILL_PICK")
		_note.text = ""
		return

	var attack := AttackDB.get_instance().get_attack(skill.SkillId)
	_name.text = attack.name if attack != null else str(skill.SkillId)
	_level.text = tr("GUILD_SKILL_LEVEL") % [skill.Level, skill.MaxLevel]
	_description.text = tr(attack.description_key) if attack != null else ""
	_requirements.text = _requirements_text(skill)
	_requirements.visible = skill.PrerequisiteSkillIds.size() > 0
	_invest.disabled = not _may_raise(skill)

	if NEEDS_STRONGHOLD.has(int(skill.SkillId)):
		_note.text = tr("GUILD_SKILL_NEEDS_STRONGHOLD")
	elif EFFECT_LATER.has(int(skill.SkillId)):
		_note.text = tr("GUILD_SKILL_EFFECT_LATER")
	else:
		_note.text = ""


func _requirements_text(skill: GuildSkillInfo) -> String:
	var parts: PackedStringArray = []
	for i in skill.PrerequisiteSkillIds.size():
		var required_id: int = skill.PrerequisiteSkillIds[i]
		var required_level: int = skill.PrerequisiteLevels[i]
		var attack := AttackDB.get_instance().get_attack(required_id)
		var required_name: String = attack.name if attack != null else str(required_id)
		var color := UiStyle.ONLINE if _tree.level_of(required_id) >= required_level else UiStyle.DANGER
		parts.append("[color=#%s]%s Lv.%d[/color]" % [color.to_html(false), required_name, required_level])
	return tr("GUILD_SKILL_REQUIRES") % ", ".join(parts)


func _selected_skill() -> GuildSkillInfo:
	if _info == null:
		return null
	for skill in _info.Skills:
		if skill.SkillId == _tree.selected_skill_id:
			return skill
	return null
