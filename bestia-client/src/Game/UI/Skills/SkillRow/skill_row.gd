extends MarginContainer

const DISABLED_MODULATE: Color = Color(0.4, 0.4, 0.4, 0.6)

@onready var _spend_skill_point_button = %SpendSkillPointButton
@onready var _icon: TextureRect = %TextureRect
@onready var _skill_name: Label = %SkillName
@onready var _skill_level: Label = %SkillLevel
@onready var _mana_label: Label = %ManaLabel
@onready var _level_minus_button: Button = %LevelMinusButton
@onready var _level_plus_button: Button = %LevelPlusButton

var attack_id: int = -1
var _disabled: bool = false


func set_data(p_attack_id: int, p_name: String, p_icon: Texture2D, p_level: int, p_max_level: int, p_mana_cost: int) -> void:
	attack_id = p_attack_id
	_skill_name.text = p_name
	_icon.texture = p_icon
	_skill_level.text = "Lv: %s / %s" % [p_level, p_max_level]
	_mana_label.text = "Mana: %s" % [p_mana_cost]


## Dims the row and blocks its interactive buttons for a skill that isn't learned/active yet,
## while still showing it so the player can see what's available.
func set_disabled(disabled: bool) -> void:
	_disabled = disabled
	self_modulate = DISABLED_MODULATE if disabled else Color.WHITE
	_spend_skill_point_button.disabled = disabled
	_level_minus_button.disabled = disabled
	_level_plus_button.disabled = disabled
