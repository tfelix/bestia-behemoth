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
@onready var _shop_win: WidgetWindow = $ShopWin
@onready var _ground_drop_zone: GroundDropZone = $GroundDropZone
@onready var _shortcuts: Shortcuts = $Shortcuts
@onready var _map_source: MapSource = $MapSource
@onready var _minimap: Minimap = $Minimap
@onready var _map_overlay: MapOverlay = $MapOverlay
@onready var _chat: Chat = $Chat
@onready var _weather: Weather = $Weather
@onready var _map_marks: MapMarks = $MapMarks
@onready var _mark_name_popup: MarkNamePopup = $MarkNamePopup
@onready var _mark_delete_popup: MarkDeletePopup = $MarkDeletePopup
@onready var _obtained_items: ObtainedItems = $ObtainedItems
@onready var _item_info_popup: ItemInfoPopup = $ItemInfoPopup

## Item ids of the map charts, from items.yml. The minimap exists exactly while one of these is carried.
const _CHART_ENABLING_ITEM_IDS := [21]

## Said once when the map server stops accepting this session. Worded here rather than in [MapSource] for the
## reason the chat refusals are worded in [code]chat.gd[/code]: the code that detects a thing should not be
## the code that phrases it, or the phrasing cannot be translated.
const _MAP_UNAVAILABLE_TEXT := "The map is unavailable - this session is no longer recognised by the map server."

## How far from the player a place or a mark still earns a bearing on the compass, in metres.
##
## About what the minimap spans at its own zoom, which is the point: the strip is a reading of the
## neighbourhood the player can already half-see, not an index of the world. Further out it would be
## answering a question the full map answers better.
const _COMPASS_RADIUS_METRES := 2000.0

## Most bearings one source may put on the strip at once. A player standing in a city is near dozens of
## named places, and a strip carrying all of them is a fence rather than a compass.
const _COMPASS_MAX_MARKS := 6

## How often the bearings are recomputed. The strip redraws itself every frame from what it holds; this is
## only how often *what it holds* is rebuilt, and a walking player does not change neighbourhood at 60 Hz.
const _COMPASS_REFRESH_SECONDS := 0.5

## Compass ink. Distinct from the wind's, and matching what the map draws each set in, so a bearing and the
## thing it points at are recognisably the same thing on both.
const _COMPASS_PLACE_COLOUR := Color(0.97, 0.93, 0.85)
const _COMPASS_MARK_COLOUR := Color(0.62, 0.85, 1.0)

## The charts the player was last seen holding, as a sorted signature. Compared rather than counted, so
## swapping one chart for another - which changes what is visible without changing how many are held -
## still drops the cache.
var _chart_signature: String = ""

## The world the marks were last loaded for, so a version arriving after a master selection - or the other
## way round - still ends with the pair loaded exactly once.
var _marks_version: String = ""
var _marks_master_id: int = 0
var _marks_loaded_version: String = ""

var _compass_refresh_countdown: float = 0.0


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

	# And the shop, for the same reason again: asking a merchant for their wares is what opens it. It needs
	# the inventory to know whether the player is holding anything a merchant would take.
	var shop := _shop_win.get_content() as Shop
	shop.inventory = inventory
	shop.shop_opened.connect(_on_shop_opened)
	shop.shop_closed.connect(_on_shop_closed)

	# The map lives beside the game rather than inside it: both views draw from one MapSource, so panning
	# the overlay warms the minimap. EntityManager is a sibling of this node under Game and is what the
	# views ask for the player's position.
	var entities := EntityManager.get_instance()
	if entities == null:
		# Said out loud because it used to be silent. Both views keep whatever they are handed for the
		# life of the scene, so a null here is not a map that recovers on the next frame - it is a map
		# stuck on the world origin, showing fog and no player marker, for the whole session.
		push_error("No EntityManager for the map views; the map cannot follow the player.")

	_minimap.setup(_map_source, entities)
	_map_overlay.setup(_map_source, entities)

	# The map draws no text of its own, so a map that has stopped working reports itself where the other
	# window-less refusals do. Connected before the first request, so a refusal of it is not missed.
	_map_source.map_unavailable.connect(_on_map_unavailable)

	# The player's own marks, wired before the first request for the same reason: /meta is what names the
	# world they are keyed to, and missing that answer would leave them unloaded for the session.
	_map_source.meta_ready.connect(_on_map_meta_ready)
	_map_marks.marks_changed.connect(_on_marks_changed)
	_minimap.set_marks(_map_marks)
	_map_overlay.set_marks(_map_marks)
	_map_overlay.mark_requested.connect(_on_mark_requested)
	_map_overlay.mark_clicked.connect(_on_mark_clicked)
	_map_overlay.place_selected.connect(_on_place_selected)

	_map_source.fetch_meta()
	_load_marks()

	# The one thing the client needs to know about its own fog is which charts it holds, and that arrives
	# with the inventory it was already being sent. No map channel message, no coverage-changed push.
	inventory.inventory_updated.connect(_on_inventory_updated.bind(inventory))
	_on_inventory_updated(inventory)

	inventory.items_gained.connect(_on_items_gained)

	# The chat reports a hover and does not word or place anything itself, the same split the map popups use.
	_chat.item_hovered.connect(_item_info_popup.show_for)
	_chat.item_hover_ended.connect(_item_info_popup.hide)

	DialogManager.show_local_once(_BASIC_SKILL_PRIMER)


func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("toggle_map"):
		_map_overlay.toggle()
		get_viewport().set_input_as_handled()


func _process(delta: float) -> void:
	_compass_refresh_countdown -= delta
	if _compass_refresh_countdown <= 0.0:
		_compass_refresh_countdown = _COMPASS_REFRESH_SECONDS
		_update_compass_marks()


## Puts the nearby world places and the player's own marks on the compass strip.
##
## Two sources rather than one merged list, because [method CompassStrip.set_marks] is keyed by source and
## they change on different schedules - the marks when the player edits one, the places when a tile arrives -
## so merging them would mean rebuilding both to change either.
func _update_compass_marks() -> void:
	var compass := _weather.compass()
	if compass == null:
		return

	var at: Variant = _minimap.player_metres()
	if at == null:
		compass.clear_marks(&"places")
		compass.clear_marks(&"marks")
		return

	var here: Vector2 = at

	var place_marks: Array = []
	for place in _map_source.places_near(here, _COMPASS_RADIUS_METRES, _COMPASS_MAX_MARKS):
		var label := _map_overlay.label_of(place)
		if label.is_empty():
			continue
		place_marks.append(_bearing_mark(here, place, label, _COMPASS_PLACE_COLOUR))

	var own_marks: Array = []
	for mark in _map_marks.all():
		var to := Vector2(float(mark["x"]), float(mark["y"]))
		if here.distance_to(to) > _COMPASS_RADIUS_METRES:
			continue
		own_marks.append(_bearing_mark(here, mark, str(mark["name"]), _COMPASS_MARK_COLOUR))
		if own_marks.size() >= _COMPASS_MAX_MARKS:
			break

	compass.set_marks(&"places", place_marks)
	compass.set_marks(&"marks", own_marks)


## One strip entry for something at a map position.
##
## The map's frame is x-east, y-north; [method CompassStrip.bearing_between] takes a [Vector3] in the game's
## frame, where north is +z. So the y of a map position becomes the z of the vector, which is the same swap
## [method MapView.player_metres] performs in the other direction and for the same reason.
func _bearing_mark(here: Vector2, at: Dictionary, label: String, colour: Color) -> Dictionary:
	var to := Vector2(float(at.get("x", 0.0)), float(at.get("y", 0.0)))

	return {
		"bearing": CompassStrip.bearing_between(
			Vector3(here.x, 0.0, here.y), Vector3(to.x, 0.0, to.y)
		),
		"colour": colour,
		"label": label,
	}


func _on_map_meta_ready(meta: Dictionary) -> void:
	_marks_version = str(meta.get("worldMapVersion", ""))
	_load_marks()


## Loads the marks once both halves of their key are known.
##
## The world version arrives from `/meta` and the master from the selection that preceded this scene, and
## neither order is guaranteed - so this is called from both and does nothing until it has both.
func _load_marks() -> void:
	var master_id := 0
	if ConnectionManager.selected_master_info != null:
		master_id = int(ConnectionManager.selected_master_info.MasterId)

	if _marks_version.is_empty() or master_id == 0:
		return

	# Compared against what was last loaded rather than against whether anything came back: a master with no
	# marks yet is the ordinary case, and a guard that keyed on the set being non-empty would reload the file
	# on every call for exactly the players who have nothing in it.
	if master_id == _marks_master_id and _marks_version == _marks_loaded_version:
		return

	_marks_master_id = master_id
	_marks_loaded_version = _marks_version
	_map_marks.use(_marks_version, master_id)


func _on_marks_changed() -> void:
	_minimap.redraw()
	_map_overlay.redraw()
	_update_compass_marks()


## Right-clicked ground: ask what to call it, and store it if the player says.
func _on_mark_requested(at: Vector2) -> void:
	if _map_marks.count() >= MapMarks.MAX_MARKS:
		# Said where the other window-less refusals are said, rather than opening the popup and rejecting
		# the answer - the player has not done anything wrong yet, and being asked for a name that cannot
		# be kept is the worse version of this.
		_chat.error_line(tr("MARKS_FULL"))
		return

	_ask_for_name(func(entered: String) -> void: _map_marks.add(entered, at))


## Right-clicked one of their own: ask whether to take it away.
##
## The same gesture that made the mark, so it has to be confirmed. A second right-click near one the player
## just placed is an easy thing to do by accident, and there is no undo for a note they wrote themselves.
func _on_mark_clicked(index: int) -> void:
	var existing: Array[Dictionary] = _map_marks.all()
	if index < 0 or index >= existing.size():
		return

	_listen_once(
		_mark_delete_popup.confirmed,
		func() -> void: _map_marks.remove(index)
	)
	_mark_delete_popup.ask(DisplayServer.mouse_get_position(), str(existing[index]["name"]))


## Opens the name popup at the cursor, with exactly one listener on the answer.
func _ask_for_name(on_submitted: Callable) -> void:
	_listen_once(_mark_name_popup.submitted, on_submitted)
	_mark_name_popup.ask(DisplayServer.mouse_get_position())


## Wires [param handler] to [param subject] as the only listener, for one firing.
##
## Both popups are asked the same question repeatedly about different marks, and each answer means whatever
## the question that opened it meant - so a listener left connected from last time would delete the mark
## before the one the player is looking at now. Cleared and reconnected per question rather than bound once.
func _listen_once(subject: Signal, handler: Callable) -> void:
	for connection in subject.get_connections():
		subject.disconnect(connection["callable"])

	subject.connect(handler, CONNECT_ONE_SHOT)


## A place picked on the map is highlighted there and refreshed onto the compass immediately, rather than
## waiting out the refresh interval - a click is the one moment the player is looking for the answer.
func _on_place_selected(_place: Dictionary) -> void:
	_update_compass_marks()


## Shows or hides both map views, and drops the tiles that depended on the old charts.
##
## Charts are the only source of map knowledge, so a player holding none would have a minimap of solid fog -
## which reads as a broken widget rather than as something to go and earn. The overlay is gated on the same
## thing for the same reason, and because an ungated one goes on asking the server for tiles of ground the
## player has no way to see.
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
	_map_overlay.set_has_chart(not held.is_empty())
	_map_source.set_chart_signature(signature)


func _on_map_unavailable() -> void:
	_chat.system_line(_MAP_UNAVAILABLE_TEXT)


## Said twice on purpose: the banner is for the player who is looking at the world and will not read the
## chat, the chat line is the record they can scroll back to and ask what the thing actually was.
func _on_items_gained(gains: Array) -> void:
	_obtained_items.show_gains(gains)

	for gain in gains:
		_chat.obtained_line(gain["item"], gain["amount"])


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


## Opened by the server like the trade window, and the inventory comes up with it for the same reason: half
## of a shop is selling, and a player who cannot see what they are carrying can only buy.
func _on_shop_opened() -> void:
	_shop_win.visible = true
	_inventory_win.visible = true
	_skills.visible = false


func _on_shop_closed() -> void:
	_shop_win.visible = false


func _on_master_profile_status_win_toggled() -> void:
	_status_win.visible = !_status_win.visible
	_inventory_win.visible = false
	_skills.visible = false
	if _status_win.visible:
		var status_content := _status_win.get_content() as StatusPoints
		if status_content:
			status_content.request_refresh()
