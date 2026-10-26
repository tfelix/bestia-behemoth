class_name AuctionHousePanel
extends PanelContainer

## The auction house: browse the shared market, sell, and collect what is owed.
##
## Opened by the server when the player clicks a house in reach, and closed by walking away, like the shop.
## Built in code, like [CompendiumPanel].

signal auction_opened()
signal auction_closed()

## How far the player may wander from where the window opened before it closes. Generous, because the
## player stood within reach of a wall and may walk along a sixteen-metre hall without meaning to leave.
const _WALK_AWAY_METRES := 10.0

enum Tab { BROWSE, SELL, COLLECT }

## Assigned by ui.gd, like the shop's: the window only exists once its WidgetWindow is ready.
var inventory: Inventory = null:
	set(value):
		inventory = value
		inventory.gold_changed.connect(_show_gold)

## The house the window is open at. Zero while closed. Every request names it.
var _house_entity_id: int = 0
var _opened_at := Vector3.ZERO

var _town: Label
var _linked: Label
var _tabs: TabContainer
var _browse: AuctionBrowsePage


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(780, 500)
	_build()

	ConnectionManager.auction_house_opened.connect(_on_opened)
	visibility_changed.connect(_on_visibility_changed)
	set_process(false)


func is_open() -> bool:
	return _house_entity_id != 0


func close_house() -> void:
	if not is_open():
		return

	_house_entity_id = 0
	set_process(false)
	auction_closed.emit()


func _process(_delta: float) -> void:
	var player := _player()
	if player == null or player.global_position.distance_to(_opened_at) > _WALK_AWAY_METRES:
		close_house()


func _on_opened(msg: AuctionHouseOpenSMSG) -> void:
	var was_open := is_open()
	_house_entity_id = msg.HouseEntityId

	var player := _player()
	_opened_at = player.global_position if player != null else Vector3.ZERO
	set_process(player != null)

	_town.text = tr("AUCTION_TITLE") % msg.TownName
	_linked.text = tr("AUCTION_LINKED") % ", ".join(msg.LinkedTownNames)

	_browse.house_entity_id = _house_entity_id
	_show_gold()

	if not was_open:
		_tabs.current_tab = Tab.BROWSE
		_browse.search()
		auction_opened.emit()


## The window's own close button only hides it.
func _on_visibility_changed() -> void:
	if not is_visible_in_tree():
		close_house()


func _show_gold(_amount: int = 0) -> void:
	if inventory != null:
		_browse.set_gold(inventory.gold())


func _player() -> Entity:
	var manager := EntityManager.get_instance()
	return manager.get_controlled_entity() if manager != null else null


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_town = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_town)

	_linked = UiStyle.label("", 11, UiStyle.MUTED)
	body.add_child(_linked)

	_tabs = TabContainer.new()
	_tabs.size_flags_vertical = Control.SIZE_EXPAND_FILL
	body.add_child(_tabs)

	_browse = AuctionBrowsePage.new()
	_tabs.add_child(_browse)
	_tabs.add_child(_placeholder_page(tr("AUCTION_SELL_EMPTY")))
	_tabs.add_child(_placeholder_page(tr("AUCTION_COLLECT_EMPTY")))
	_tabs.set_tab_title(Tab.BROWSE, tr("AUCTION_TAB_BROWSE"))
	_tabs.set_tab_title(Tab.SELL, tr("AUCTION_TAB_SELL"))
	_tabs.set_tab_title(Tab.COLLECT, tr("AUCTION_TAB_COLLECT"))


func _placeholder_page(text: String) -> Control:
	var page := CenterContainer.new()
	page.add_child(UiStyle.label(text, 13, UiStyle.MUTED))
	return page
