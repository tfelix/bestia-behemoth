extends Control

@onready var _inventory: Control = $Inventory
@onready var _skills: WidgetWindow = $SkillsWin


func _on_master_profile_inventory_win_toggled() -> void:
	_inventory.visible = !_inventory.visible
	_skills.visible = false


func _on_master_profile_skills_win_toggled() -> void:
	_skills.visible = !_skills.visible
	_inventory.visible = false
	if _skills.visible:
		var skills_content := _skills.get_content() as Skills
		if skills_content:
			skills_content.request_refresh()
