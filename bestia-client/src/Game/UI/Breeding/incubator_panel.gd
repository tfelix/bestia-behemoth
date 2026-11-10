class_name IncubatorPanel
extends PanelContainer

## An Incubator of the played master: put an egg in from the bag, watch it warm, take it out. Opened by the
## server when the owner clicks the incubator, and kept current by it while open. Built in code, like
## [PostOfficePanel].

signal incubator_opened()

## The bestia egg. Must match items.yml.
const _BESTIA_EGG_ITEM_ID := 129

## Set by the UI, which owns the inventory window. Untyped like [member InventoryItem.inventory], for the
## same load cycle.
var inventory = null

var _structure_id: int = 0
var _ends_at_msec: int = 0

var _egg: Label
var _progress: ProgressBar
var _timer: Label
var _waiting: Label
var _take_egg: Button
var _bag_eggs: VBoxContainer
var _no_eggs: Label


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(360, 300)
	_build()

	ConnectionManager.incubator_received.connect(_on_incubator)
	ConnectionManager.bestia_egg_received.connect(_on_egg_read)
	set_process(false)


func _process(_delta: float) -> void:
	_timer.text = BreedingText.duration(maxi(_ends_at_msec - Time.get_ticks_msec(), 0))


func _on_incubator(msg: IncubatorSMSG) -> void:
	var is_new := msg.StructureId != _structure_id or not is_visible_in_tree()
	_structure_id = msg.StructureId

	var has_egg := msg.Egg != null
	_egg.text = BreedingText.egg_detail(msg.Egg) if has_egg else tr("INCUBATOR_EMPTY")
	_progress.visible = has_egg
	if has_egg:
		UiStyle.set_bar(_progress, msg.Egg.HatchDoneMs, msg.Egg.HatchTotalMs)

	_ends_at_msec = Time.get_ticks_msec() + msg.RemainingMs
	_timer.visible = has_egg and not msg.WaitingForSlot
	set_process(_timer.visible)
	_waiting.visible = msg.WaitingForSlot
	_take_egg.visible = has_egg
	_list_bag_eggs(not has_egg)

	if is_new:
		incubator_opened.emit()


## The eggs the master holds, each with a button to put it in. Each is inspected so the row can name it.
func _list_bag_eggs(may_place: bool) -> void:
	for child in _bag_eggs.get_children():
		_bag_eggs.remove_child(child)
		child.queue_free()

	var held: Array = []
	if may_place and inventory != null:
		var master_entity_id: int = EntityManager.get_instance().get_owned_master_entity_id()
		held = inventory.held_instances(master_entity_id).filter(
			func(item): return item.item.item_id == _BESTIA_EGG_ITEM_ID
		)

	_no_eggs.visible = may_place and held.is_empty()
	for item in held:
		var button := Button.new()
		button.text = tr("INCUBATOR_PLACE_EGG")
		button.set_meta("unique_id", item.unique_id)
		button.pressed.connect(_on_place_pressed.bind(item.unique_id))
		_bag_eggs.add_child(button)
		if ConnectionManager.is_ready_to_send():
			ConnectionManager.inspect_egg(item.unique_id)


func _on_egg_read(message: BestiaEggSMSG) -> void:
	if message.Egg == null:
		return
	for button in _bag_eggs.get_children():
		if button.get_meta("unique_id") == message.Egg.UniqueId:
			button.text = "%s: %s" % [tr("INCUBATOR_PLACE_EGG"), BreedingText.egg_title(message.Egg)]
			button.tooltip_text = BreedingText.egg_detail(message.Egg)


func _on_place_pressed(unique_id: int) -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.place_egg(_structure_id, unique_id)


func _on_take_egg_pressed() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.take_egg(_structure_id)


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_egg = UiStyle.label("", 12)
	body.add_child(_egg)
	_progress = UiStyle.bar(UiStyle.EXP_FILL, 8)
	body.add_child(_progress)
	_timer = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_timer)
	_waiting = UiStyle.label(tr("INCUBATOR_WAITING_FOR_SLOT"), 12, UiStyle.DANGER)
	_waiting.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.add_child(_waiting)

	_bag_eggs = UiStyle.vbox(4)
	body.add_child(_bag_eggs)
	_no_eggs = UiStyle.label(tr("INCUBATOR_NO_EGGS"), 12, UiStyle.MUTED)
	body.add_child(_no_eggs)
	body.add_child(UiStyle.spacer())

	_take_egg = Button.new()
	_take_egg.text = tr("BREEDING_TAKE_EGG")
	_take_egg.pressed.connect(_on_take_egg_pressed)
	body.add_child(_take_egg)
