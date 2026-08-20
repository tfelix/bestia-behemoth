extends Control

## The client-only primer shown to each master once, explaining that Basic Skill is what unlocks chat and
## parties - which is otherwise something a new player discovers by being refused.
const _BASIC_SKILL_PRIMER := "BASIC_SKILL_PRIMER"

@onready var _inventory_win: WidgetWindow = $InventoryWin
@onready var _skills: WidgetWindow = $SkillsWin
@onready var _equipment_win: WidgetWindow = $EquipmentWin
@onready var _status_win: WidgetWindow = $StatusWin
@onready var _crafting_win: WidgetWindow = $CraftingWin
@onready var _trade_win: WidgetWindow = $TradeWin
@onready var _ground_drop_zone: GroundDropZone = $GroundDropZone
@onready var _shortcuts: Shortcuts = $Shortcuts
@onready var _map_source: MapSource = $MapSource
@onready var _minimap: Minimap = $Minimap
@onready var _map_overlay: MapOverlay = $MapOverlay

## Item ids of the map charts, from items.yml. The minimap exists exactly while one of these is carried.
const _CHART_ENABLING_ITEM_IDS := [21]

## The charts the player was last seen holding, as a sorted signature. Compared rather than counted, so
## swapping one chart for another - which changes what is visible without changing how many are held -
## still drops the cache.
var _chart_signature: String = ""


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

	# Same again for trading: two people agreeing to trade is what opens the window, and the exchange ending
	# is what closes it, so it has no toggle either. It needs the inventory to price a partial stack - the
	# server is asked for an amount the player is actually holding.
	var trade := _trade_win.get_content() as Trade
	trade.inventory = inventory
	trade.trade_opened.connect(_on_trade_opened)
	trade.trade_closed.connect(_on_trade_closed)

	# The map lives beside the game rather than inside it: both views draw from one MapSource, so panning
	# the overlay warms the minimap. EntityManager is a sibling of this node under Game and is what the
	# views ask for the player's position.
	var entities := EntityManager.get_instance()
	
	get_parent().get_node_or_null("EntityManager")
	_minimap.setup(_map_source, entities)
	_map_overlay.setup(_map_source, entities)
	_map_source.fetch_meta()

	# The one thing the client needs to know about its own fog is which charts it holds, and that arrives
	# with the inventory it was already being sent. No map channel message, no coverage-changed push.
	inventory.inventory_updated.connect(_on_inventory_updated.bind(inventory))
	_on_inventory_updated(inventory)

	DialogManager.show_local_once(_BASIC_SKILL_PRIMER)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("toggle_map"):
		_map_overlay.toggle()
		get_viewport().set_input_as_handled()


## Shows or hides the minimap, and drops the tiles that depended on the old charts.
##
## Charts are the only source of map knowledge, so a player holding none would have a minimap of solid fog -
## which reads as a broken widget rather than as something to go and earn.
##
## Only the personal tiles go. A fully charted tile is the same picture for everybody and does not become a
## different one when its owner charts more land, so charting does not throw away the part of the map that
## was already complete.
func _on_inventory_updated(inventory: Inventory) -> void:
	var held: Array[String] = []
	for item in inventory.held_instances():
		if item.item.item_id in _CHART_ENABLING_ITEM_IDS:
			held.append("%d:%d" % [item.item.item_id, item.player_item_id])

	held.sort()
	var signature := ",".join(held)
	if signature == _chart_signature:
		return

	_chart_signature = signature
	_minimap.set_has_chart(not held.is_empty())
	_map_source.set_chart_signature(signature)


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


## Opened by the server, like the crafting window. The inventory comes up with it because a trade is made of
## dragging out of it, and closing it would leave the player nothing to offer.
func _on_trade_opened() -> void:
	_trade_win.visible = true
	_inventory_win.visible = true
	_skills.visible = false


func _on_trade_closed() -> void:
	_trade_win.visible = false


func _on_master_profile_status_win_toggled() -> void:
	_status_win.visible = !_status_win.visible
	_inventory_win.visible = false
	_skills.visible = false
	if _status_win.visible:
		var status_content := _status_win.get_content() as StatusPoints
		if status_content:
			status_content.request_refresh()
