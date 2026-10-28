extends PanelContainer
class_name Inventory

signal inventory_updated()

## Item kinds the bag gained since the last snapshot, as [code]{item: ItemResource, amount: int}[/code].
##
## Separate from [signal inventory_updated], which also fires with no snapshot behind it - on the login
## re-emit and from the local mutators - and so cannot say what changed.
signal items_gained(gains: Array[Dictionary])

## The gold coins the controlled entity carries, each time that number changes.
signal gold_changed(amount: int)

## The player asked to swap goods with one of their own entities from outside this window - the world's
## right-click menu - so the window has to come up.
signal exchange_opened()

## Gold is carried as coins like any other item; items.yml `gold_coin`, the server's shop currency.
const GOLD_COIN_ITEM_ID := 27

## How close another of the player's own entities has to stand to swap goods with the driven one: the server's
## ExchangeReach. It only counts as gone again past [constant _EXCHANGE_LEAVE_METRES], so a bestia pacing
## at the edge does not make the exchange column flicker.
const _EXCHANGE_REACH_METRES := 5.0
const _EXCHANGE_LEAVE_METRES := 6.0
const _EXCHANGE_REACH_HEIGHT := 3.0
const _REACH_POLL_SECONDS := 0.25

var InventoryItem = preload("res://Game/UI/Inventory/InventoryItem/InventoryItem.tscn")
var DropAmountDialogScn = preload("res://Game/UI/Inventory/DropAmountDialog/DropAmountDialog.tscn")

## This is only for testing. In reality you will receive the items from the server.
@export var preset_items: Array[InventoryItemResource]
@export var selected_entity_id: int

## Assigned at runtime by Game/UI/ui.gd - both live inside WidgetWindows that only instantiate
## their content in _ready(), so neither can be reached through an editor-wired NodePath.
var equipment: Equipment = null
var equipment_window: WidgetWindow = null

@onready var _search: LineEdit = %Search
@onready var _sort: OptionButton = %Sort
@onready var _usable_grid: GridContainer = %UsableGrid
@onready var _equip_grid: GridContainer = %EquipGrid
@onready var _etc_grid: GridContainer = %EtcGrid
@onready var _partner_strip: HBoxContainer = %PartnerStrip
@onready var _exchange: ExchangeColumn = %ExchangeColumn

var _items: Dictionary[int, Array]

## The orders the sort menu offers, as [code]{label, before: Callable(a, b) -> bool}[/code] over held
## [InventoryItemResource]s, in menu order. A list rather than an enum so another window can add its own.
var _sort_keys: Array[Dictionary] = []
var _reported_gold := 0

## Carry limit per owned entity, as the server last sent it. Only the limit is read: the load is summed off
## the bag itself, which is always the newer of the two.
var _carry_max: Dictionary[int, int]

## The own entity whose bag the exchange column shows, or 0. Kept while it walks out of reach, so the column
## comes back on its own when it returns.
var _partner_id := 0

## Which own entities stand within reach of the driven one, with the hysteresis the constants describe.
var _in_reach: Dictionary[int, bool]
var _reach_poll := 0.0

func _ready() -> void:
	# How the world's right-click menu, built long after this window, finds it.
	add_to_group("inventory")
	ConnectionManager.connect("entity_received", _on_entity_received)
	# Every change to a bag, and every switch of the controlled entity, already goes out as this signal.
	inventory_updated.connect(_report_gold_change)
	_exchange.item_dropped.connect(_on_dropped_on_partner)
	_exchange.item_requested.connect(_on_partner_row_requested)
	_search.text_changed.connect(func(_text: String) -> void: _render_items())
	_sort.item_selected.connect(func(_index: int) -> void: _render_items())
	_add_sort_key("INVENTORY_SORT_NAME", _by_name)
	_add_sort_key("INVENTORY_SORT_AMOUNT", func(a, b) -> bool: return a.amount > b.amount)
	_add_sort_key("INVENTORY_SORT_WEIGHT", func(a, b) -> bool: return _weight_of(a) > _weight_of(b))

	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)
		entity_manager.owned_bestias_changed.connect(func(_bestias) -> void: _rebuild_partner_strip())

	# We usually overlook the first inventory update because we did not yet
	# fully load but the server already send it out. So we request it again to be
	# sure.
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.get_inventory()

	_render_items()


## The entity the player drives, which is what every read of this inventory is keyed on. On login that is the
## master, and the moment the items the server already sent become reachable at all.
##
## The order is not a race: the UI subtree readies before EntityManager, so `getInventory` goes out before
## `getSelf` and the zone answers in that order. Until this runs, [member selected_entity_id] is 0 and both
## [method held_instances] and [method get_item_count] read an entity nobody has heard of, so they answer
## "carrying nothing" for an inventory that is sitting right there.
##
## Hence the emit, which is not cosmetic: the only inventory push of a login has already been and gone by
## now, so a listener that concluded "nothing carried" from it is never told otherwise for the rest of the
## session. That is what hid the minimap and made M do nothing while a chart sat in the inventory window,
## and what left the shortcut bar reading zero for items in hand.
func _on_controlled_entity_changed(previous_id: int, entity_id: int) -> void:
	selected_entity_id = entity_id
	# Taking control of the partner turns the exchange around rather than ending it.
	if entity_id == _partner_id:
		_partner_id = previous_id
	_render_items()
	_update_reach()
	inventory_updated.emit()


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is CarryCapacityComponentSMSG:
		_carry_max[msg.EntityId] = msg.Max
		if msg.EntityId == _partner_id:
			_render_partner()
		return

	# Skip when it is not adressing our own entity.
	if msg is InventoryComponentSMSG:
		# A bag arriving for the first time is what the player already owns rather than news, so it seeds and
		# says nothing - the same distinction health_bar.gd draws. _items.has() is that flag already; the only
		# thing that could lie to it is add_item creating the key early, and nothing calls add_item.
		var seeded := _items.has(msg.EntityId)

		# Counted here rather than further down because the rebuild clears this very array in place.
		var held_before := _count_by_item(_items.get(msg.EntityId, []))

		var selected_entity_items = _items.get_or_add(msg.EntityId, [])
		selected_entity_items.clear()
		var item_db = ItemDB.get_instance()
		for itemMsg in msg.Items:
			# Get the ItemResource from the database
			var item_resource = item_db.get_item(itemMsg.ItemId)
			if item_resource == null:
				printerr("Inventory: Item with ID %s not found in ItemDB" % [itemMsg.ItemId])
				continue

			# Create an InventoryItemResource
			var inv_item = InventoryItemResource.new()
			inv_item.item = item_resource
			inv_item.amount = itemMsg.Amount
			inv_item.player_item_id = itemMsg.PlayerItemId
			inv_item.unique_id = itemMsg.UniqueId
			inv_item.crafted_by = itemMsg.CraftedBy
			inv_item.crafted_in = itemMsg.CraftedIn
			inv_item.equipped = itemMsg.Equipped
			inv_item.durability = itemMsg.Durability
			inv_item.max_durability = itemMsg.MaxDurability
			inv_item.slots = itemMsg.Slots
			inv_item.upgrade_level = itemMsg.UpgradeLevel
			inv_item.item_level = itemMsg.ItemLevel
			inv_item.rolled_stats = Array(itemMsg.RolledStats)
			selected_entity_items.append(inv_item)
		_render_items()
		if msg.EntityId == _partner_id:
			_render_partner()
		inventory_updated.emit()

		# Gated on the entity the player drives: every owned entity's bag is synced here, and only that one's
		# gains are something the player just did.
		if seeded and msg.EntityId == selected_entity_id:
			_announce_gains(held_before, _count_by_item(selected_entity_items))


## How many of each item kind are held, worn or not.
##
## Worn gear counts. The server keeps it in this same list and only flags it, so leaving it out would make
## taking a piece off read as finding one. [method _render_items] skips it for an unrelated reason - so the
## same physical item cannot be dragged out of two windows.
##
## Summed per kind rather than per instance, which is what makes a second sword - its own row, its own
## [member InventoryItemResource.player_item_id] - come out as one more sword.
func _count_by_item(items: Array) -> Dictionary[int, int]:
	var counts: Dictionary[int, int] = {}
	for inv_item in items:
		var item_id: int = inv_item.item.item_id
		counts[item_id] = counts.get(item_id, 0) + inv_item.amount

	return counts


## Reports what the bag gained between two snapshots.
##
## Gains only: a bag that lost something lost it to the player spending, dropping or trading it, which they
## do not need telling. Equipping changes neither side, since worn items are counted.
func _announce_gains(before: Dictionary[int, int], after: Dictionary[int, int]) -> void:
	var item_db = ItemDB.get_instance()
	var gains: Array[Dictionary] = []

	for item_id in after:
		var gained: int = after[item_id] - before.get(item_id, 0)
		if gained > 0:
			# Certain to resolve: an item the catalogue does not carry never got into the list to be counted.
			gains.append({"item": item_db.get_item(item_id), "amount": gained})

	if not gains.is_empty():
		items_gained.emit(gains)


## Re-renders from the last known server state. Also connected to Equipment.equipment_updated, since
## putting an item on takes it out of this list without the inventory itself changing.
func refresh() -> void:
	_render_items()


func _render_items() -> void:
	# Cleared first, so a switch to an entity whose bag has not arrived yet does not show the previous one's.
	for grid in [_usable_grid, _equip_grid, _etc_grid]:
		for child in grid.get_children():
			child.queue_free()

	if !_items.has(selected_entity_id):
		return

	for item in _shown_items():
		var inv_item = InventoryItem.instantiate()
		inv_item.amount = item.amount
		inv_item.item = item.item
		inv_item.unique_id = item.unique_id
		inv_item.player_item_id = item.player_item_id
		inv_item.crafted_by = item.crafted_by
		inv_item.crafted_in = item.crafted_in
		inv_item.durability = item.durability
		inv_item.max_durability = item.max_durability
		inv_item.slots = item.slots
		inv_item.upgrade_level = item.upgrade_level
		inv_item.item_level = item.item_level
		inv_item.rolled_stats = item.rolled_stats
		inv_item.inventory = self
		inv_item.drop_target = self
		inv_item.move_requested.connect(_on_own_row_requested.bind(inv_item))
		match item.item.type:
			ItemResource.ItemType.USABLE:
				_usable_grid.add_child(inv_item)
			ItemResource.ItemType.EQUIP:
				_equip_grid.add_child(inv_item)
			ItemResource.ItemType.ETC:
				_etc_grid.add_child(inv_item)


## The held items the grids show: matching the search, in the chosen order.
##
## A worn item is still held (the server keeps it in the same container, only flagged), but showing it here as
## well would let the player drag the same physical item twice.
func _shown_items() -> Array:
	var query := _search.text.strip_edges().to_lower()
	var shown := _items[selected_entity_id].filter(
		func(item) -> bool: return not item.equipped and (query.is_empty() or _name_of_item(item).to_lower().contains(query))
	)

	var key: Dictionary = _sort_keys[_sort.selected] if _sort.selected >= 0 else _sort_keys[0]
	var before: Callable = key.before
	# Ties fall back to the name, so equal piles do not swap places on every redraw.
	shown.sort_custom(func(a, b) -> bool: return before.call(a, b) or (not before.call(b, a) and _by_name(a, b)))

	return shown


func _add_sort_key(label: String, before: Callable) -> void:
	_sort_keys.append({"label": label, "before": before})
	_sort.add_item(tr(label))


func _by_name(a, b) -> bool:
	return _name_of_item(a).naturalnocasecmp_to(_name_of_item(b)) < 0


func _name_of_item(held) -> String:
	return tr(held.item.name_key)


func _weight_of(held) -> int:
	return held.item.weight * held.amount


## Double-clicking an equipment item while the equipment window is open equips it into the slot the
## item itself declares, instead of falling through to "use". Returns false when that didn't apply,
## so the caller can do the normal use-item thing.
func try_quick_equip(item: ItemResource, player_item_id: int) -> bool:
	if equipment == null or equipment_window == null or not equipment_window.visible:
		return false
	if item.type != ItemResource.ItemType.EQUIP:
		return false

	return equipment.equip_to_own_slot(item.item_id, player_item_id)


# ------------------------------------------------------------------ exchange

## True while the exchange column shows another own entity's bag to swap goods with.
func is_exchanging() -> bool:
	return _partner_id != 0 and _in_reach.get(_partner_id, false)


## Opens the exchange with [param entity_id], one of the player's own entities - from the partner strip, or
## from right-clicking it in the world. The same partner again closes it.
func exchange_with(entity_id: int) -> void:
	_partner_id = 0 if entity_id == _partner_id else entity_id
	_update_reach()
	_rebuild_partner_strip()
	_render_partner()


## [method exchange_with], but always opening, and bringing the window up with it.
func open_exchange_with(entity_id: int) -> void:
	if entity_id != _partner_id:
		exchange_with(entity_id)
	exchange_opened.emit()


func _process(delta: float) -> void:
	if not is_visible_in_tree():
		return

	_reach_poll -= delta
	if _reach_poll > 0.0:
		return

	_reach_poll = _REACH_POLL_SECONDS
	_update_reach()


## Re-reads who stands within reach of the driven entity, and redraws the strip and the column when that changed.
## An own entity out of view counts as out of reach: the client has no position for it.
func _update_reach() -> void:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	var driven := entity_manager.get_entity(selected_entity_id)
	var reach: Dictionary[int, bool] = {}
	for entity_id in entity_manager.get_owned_entity_ids():
		if entity_id == selected_entity_id or entity_id == 0:
			continue

		var other := entity_manager.get_entity(entity_id)
		var was_near: bool = _in_reach.get(entity_id, false)
		reach[entity_id] = driven != null and other != null and _within_reach(driven, other, was_near)

	if reach == _in_reach:
		return

	_in_reach = reach
	_rebuild_partner_strip()
	_render_partner()


func _within_reach(one: Entity, other: Entity, was_near: bool) -> bool:
	var a := one.get_logical_position()
	var b := other.get_logical_position()
	var limit := _EXCHANGE_LEAVE_METRES if was_near else _EXCHANGE_REACH_METRES

	return Vector2(a.x - b.x, a.z - b.z).length() <= limit and absf(a.y - b.y) <= _EXCHANGE_REACH_HEIGHT


## One button per other own entity, coloured by its slot; greyed out while it stands too far away.
func _rebuild_partner_strip() -> void:
	for child in _partner_strip.get_children():
		_partner_strip.remove_child(child)
		child.queue_free()

	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	var owned := entity_manager.get_owned_entity_ids()
	for slot in owned.size():
		var entity_id: int = owned[slot]
		if entity_id == selected_entity_id or entity_id == 0:
			continue

		var button := Button.new()
		button.text = tr("EXCHANGE_WITH") % _name_of(entity_id)
		button.toggle_mode = true
		button.button_pressed = entity_id == _partner_id
		button.disabled = not _in_reach.get(entity_id, false)
		button.tooltip_text = tr("EXCHANGE_TOO_FAR") if button.disabled else tr("EXCHANGE_HINT")
		button.modulate = OwnedSlots.color_of(slot)
		button.pressed.connect(exchange_with.bind(entity_id))
		_partner_strip.add_child(button)

	_partner_strip.visible = _partner_strip.get_child_count() > 0


func _render_partner() -> void:
	_exchange.visible = is_exchanging()
	if not _exchange.visible:
		return

	_exchange.show_bag(_name_of(_partner_id), _items.get(_partner_id, []), _bag_weight(_partner_id), _carry_max.get(_partner_id, 0))


func _name_of(entity_id: int) -> String:
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return "?"

	if entity_id == entity_manager.get_owned_master_entity_id():
		return ConnectionManager.selected_master_info.Name if ConnectionManager.selected_master_info != null else tr("BESTIA_PARTY_MASTER")

	for bestia in entity_manager.get_owned_bestias():
		if bestia.EntityId == entity_id:
			return OwnedSlots.name_of(bestia)

	return "?"


func _bag_weight(entity_id: int) -> int:
	var total := 0
	for inv_item in _items.get(entity_id, []):
		total += inv_item.amount * inv_item.item.weight

	return total


## How many of [param wanted] the receiver can still carry. An entity whose limit has not arrived yet is not
## second-guessed: the server checks every move anyway, this only spares the player a refusal.
func _fitting(entity_id: int, item: ItemResource, wanted: int) -> int:
	var limit: int = _carry_max.get(entity_id, 0)
	if limit <= 0 or item.weight <= 0:
		return wanted

	return clampi((limit - _bag_weight(entity_id)) / item.weight, 0, wanted)


func _can_drop_data(_at_position: Vector2, data: Variant) -> bool:
	return is_exchanging() and typeof(data) == TYPE_DICTIONARY and data.get("source") == "exchange_partner"


## A partner's item dragged into the inventory comes over. Holding Ctrl on the drop asks how many.
func _drop_data(_at_position: Vector2, data: Variant) -> void:
	var amount: int = 0 if Input.is_key_pressed(KEY_CTRL) else data.get("amount", 0)
	_move(_partner_id, selected_entity_id, data.get("player_item_id", 0), data.get("id"), data.get("amount", 0), amount)


func _on_dropped_on_partner(data: Dictionary) -> void:
	var amount: int = 0 if Input.is_key_pressed(KEY_CTRL) else data.get("amount", 0)
	_move(selected_entity_id, _partner_id, data.get("player_item_id", 0), data.get("id"), data.get("amount", 0), amount)


func _on_own_row_requested(amount: int, row: Control) -> void:
	if is_exchanging():
		_move(selected_entity_id, _partner_id, row.player_item_id, row.item.item_id, row.amount, amount)


func _on_partner_row_requested(row: Control, amount: int) -> void:
	_move(_partner_id, selected_entity_id, row.player_item_id, row.item.item_id, row.amount, amount)


## Sends one move of up to [param amount] of the [param held] in the giver's bag, never more than the receiver
## can carry. [param amount] 0 asks the player first, offering at most what fits.
func _move(from_id: int, to_id: int, player_item_id: int, item_id: int, held: int, amount: int) -> void:
	if not is_exchanging() or held <= 0:
		return

	var item: ItemResource = ItemDB.get_instance().get_item(item_id)
	var fits := _fitting(to_id, item, held)
	if fits <= 0:
		_exchange.flash_refusal()
		return

	if amount > 0 or fits == 1:
		ConnectionManager.transfer_item(from_id, to_id, player_item_id, mini(maxi(amount, 1), fits))
		return

	var dialog = DropAmountDialogScn.instantiate() as DropAmountDialog
	add_child(dialog)
	dialog.amount_confirmed.connect(
		func(_id: int, picked: int) -> void: ConnectionManager.transfer_item(from_id, to_id, player_item_id, picked)
	)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	dialog.open_for(item, fits, tr("EXCHANGE_MOVE_HOW_MANY"))


func is_initialized_for_current_entity() -> bool:
	return _items.has(selected_entity_id)


## Held items that carry per-instance state and are not being worn - what the crafting window offers as a
## target for a repair, an upgrade or a rune slot.
##
## Worn gear is excluded because the server refuses it: you take a piece off to work on it, the same rule the
## item container applies to dropping and consuming. A plain stack is excluded because it has no instance at
## all, so there is nothing per-item to change. A partly burnt torch is excluded because the server refuses
## it too: its wear is burn time, and no craft gives that back.
func held_instances(entity_id: int = selected_entity_id) -> Array:
	if not _items.has(entity_id):
		return []

	return _items[entity_id].filter(
		func(item): return item.unique_id != 0 and not item.equipped and not (item.item != null and item.item.gives_light())
	)


func gold() -> int:
	return _count_by_item(_items.get(selected_entity_id, [])).get(GOLD_COIN_ITEM_ID, 0)


func _report_gold_change() -> void:
	var amount := gold()
	if amount == _reported_gold:
		return

	_reported_gold = amount
	gold_changed.emit(amount)


## The pile or copy of [param item_id] a shortcut uses: the first one held and not worn, or 0 for none.
func first_held(item_id: int) -> int:
	for inv_item in _items.get(selected_entity_id, []):
		if inv_item.item.item_id == item_id and not inv_item.equipped:
			return inv_item.player_item_id

	return 0


func get_item_count(item_id: int) -> int:
	if !_items.has(selected_entity_id):
		return 0
	
	var selected_entity_items = _items[selected_entity_id]

	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_id:
			return inv_item.amount
	return 0


func add_item(item_resource: ItemResource, amount: int) -> void:
	var selected_entity_items = _items.get_or_add(selected_entity_id, [])

	# Check if item already exists
	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_resource.item_id:
			inv_item.amount += amount
			inventory_updated.emit()
			return

	# Add new item
	var new_inv_item = InventoryItemResource.new()
	new_inv_item.item = item_resource
	new_inv_item.amount = amount
	selected_entity_items.append(new_inv_item)
	inventory_updated.emit()


func remove_item(item_id: int, amount: int) -> bool:
	var selected_entity_items = _items[selected_entity_id]

	if selected_entity_items == null:
		return false

	for inv_item in selected_entity_items:
		if inv_item.item.item_id == item_id:
			if inv_item.amount >= amount:
				inv_item.amount -= amount
				if inv_item.amount <= 0:
					selected_entity_items.erase(inv_item)
				inventory_updated.emit()
				return true
			return false
	return false
