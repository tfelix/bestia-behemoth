extends PanelContainer


@onready var _search_line_edit = %SearchLineEdit 
@onready var _skill_rows = %SkillRows


func _on_clear_button_pressed() -> void:
	_search_line_edit.text = ""
	_perform_skill_search()


func _perform_skill_search() -> void:
	# todo
	pass
