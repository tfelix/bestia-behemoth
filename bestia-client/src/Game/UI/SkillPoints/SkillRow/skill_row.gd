extends HBoxContainer

@export var skill_shortcode: String
@export var skill_points: int = 0

var available_skill_points: int = 0
var prepared_skill_points: int = 0

@onready var _skill_label = %SkillLabel
@onready var _value_label = %ValueLabel


func _on_level_button_pressed() -> void:
	prepared_skill_points += 1
	_display_value()


func _ready() -> void:
	_skill_label.text = skill_shortcode
	_value_label.text = "%s" % skill_points
	_display_value()


func _display_value() -> void:
	if prepared_skill_points > 0:
		_value_label.text = "%s (+%s)" % [skill_points, prepared_skill_points]
	else: 
		_value_label.text = "%s" % skill_points
