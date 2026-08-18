extends Control

## The client-only primer shown to each master once, explaining that Basic Skill is what unlocks chat and
## parties - which is otherwise something a new player discovers by being refused.
const _BASIC_SKILL_PRIMER := "BASIC_SKILL_PRIMER"

@onready var _inventory_win: WidgetWindow = $InventoryWin
@onready var _skills: WidgetWindow = $SkillsWin
@onready var _equipment_win: WidgetWindow = $EquipmentWin
@onready var _status_win: WidgetWindow = $StatusWin
@onready var _crafting_win: WidgetWindow = $CraftingWin
@onready var _ground_drop_zone: GroundDropZone = $GroundDropZone
@onready var _shortcuts: Shortcuts = $Shortcuts


## GroundDropZone and Shortcuts can't get their Inventory reference from an editor-wired
## NodePath: the Inventory node only comes into existence at runtime, when WidgetWindow
## instantiates its content in _ready(). So it's fetched here and assigned in code instead.
## The Inventory <-> Equipment pair is wired the same way and for the same reason: the inventory
## needs to know whether the equipment window is open (double-click then equips instead of uses)
## and which items are currently worn.
func _ready() -> void:
	var inventory := _inventory_win.get_content() as Inventory
	_ground_drop_zone.inventory = inventory
	_shortcuts.inventory = inventory

	var equipment := _equipment_win.get_content() as Equipment
	inventory.equipment = equipment
	inventory.equipment_window = _equipment_win
	equipment.equipment_updated.connect(inventory.refresh)

	# The crafting window has no toggle of its own: there is nothing to show until the server answers a
	# crafting-skill activation with what can be made, so the message is what opens it.
	var crafting := _crafting_win.get_content() as Crafting
	crafting.inventory = inventory
	crafting.recipes_offered.connect(_on_recipes_offered)

	# Queued rather than shown, so it lands behind the server's own welcome dialog instead of racing it.
	# Everything in it is static - what Basic Skill unlocks at which rank - so it needs nothing from the
	# server and is client-only. See DialogManager.show_local_once.
	DialogManager.show_local_once(_BASIC_SKILL_PRIMER)


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


## Unlike Skills, the equipment window deliberately does not close the inventory: equipping works by
## dragging from one into the other, so both have to be visible at the same time.
func _on_master_profile_equipment_win_toggled() -> void:
	_equipment_win.visible = !_equipment_win.visible
	_skills.visible = false


## Brought forward by the server rather than by the player, so activating Carpentry at a workbench opens the
## window whether or not it was already up. The skills window closes because the player almost certainly
## activated the skill from it and would otherwise be reading two lists at once.
func _on_recipes_offered() -> void:
	_crafting_win.visible = true
	_skills.visible = false


func _on_master_profile_status_win_toggled() -> void:
	_status_win.visible = !_status_win.visible
	_inventory_win.visible = false
	_skills.visible = false
	if _status_win.visible:
		var status_content := _status_win.get_content() as StatusPoints
		if status_content:
			status_content.request_refresh()
