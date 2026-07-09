extends Control

@onready var _inventory_win: WidgetWindow = $InventoryWin
@onready var _skills: WidgetWindow = $SkillsWin
@onready var _ground_drop_zone: GroundDropZone = $GroundDropZone
@onready var _shortcuts: Shortcuts = $Shortcuts


## GroundDropZone and Shortcuts can't get their Inventory reference from an editor-wired
## NodePath: the Inventory node only comes into existence at runtime, when WidgetWindow
## instantiates its content in _ready(). So it's fetched here and assigned in code instead.
func _ready() -> void:
	var inventory := _inventory_win.get_content() as Inventory
	_ground_drop_zone.inventory = inventory
	_shortcuts.inventory = inventory


func _on_master_profile_inventory_win_toggled() -> void:
	_inventory_win.visible = !_inventory_win.visible
	_skills.visible = false


func _on_master_profile_skills_win_toggled() -> void:
	_skills.visible = !_skills.visible
	_inventory_win.visible = false
	if _skills.visible:
		var skills_content := _skills.get_content() as Skills
		if skills_content:
			skills_content.request_refresh()
