class_name BreedingFarmPanel
extends PanelContainer

## A Breeding Farm of the played master: pick a female and a male, start a clutch with a Nesting Bundle, take
## the egg. Opened by the server when the owner clicks the farm, and kept current by it while open. Built in
## code, like [PostOfficePanel].

signal farm_opened()

## Must match the server's BreedingFarmService.MIN_LEVEL.
const _MIN_PARENT_LEVEL := 10

const _SEX_MALE := 1
const _SEX_FEMALE := 2

## The BreedingFarmPhase ordinals of breeding_farm_smsg.proto.
enum Phase { IDLE, GATHERING, BREEDING, LAYING, EGG_READY }

var _structure_id: int = 0
var _ends_at_msec: int = 0

var _status: Label
var _timer: Label
var _mother: OptionButton
var _father: OptionButton
var _egg: Label
var _start: Button
var _release: Button
var _take_egg: Button


func _ready() -> void:
	add_theme_stylebox_override("panel", UiStyle.flat(UiStyle.PANEL_BG, 0, 0))
	custom_minimum_size = Vector2(360, 300)
	_build()

	ConnectionManager.breeding_farm_received.connect(_on_farm)
	set_process(false)


func _process(_delta: float) -> void:
	_timer.text = BreedingText.duration(maxi(_ends_at_msec - Time.get_ticks_msec(), 0))


func _on_farm(msg: BreedingFarmSMSG) -> void:
	var is_new := msg.StructureId != _structure_id or not is_visible_in_tree()
	_structure_id = msg.StructureId

	_status.text = tr("BREEDING_FARM_PHASE_%d" % msg.Phase)
	if msg.Phase == Phase.IDLE:
		_status.text += "\n" + tr("BREEDING_FARM_NEEDS_BUNDLE")

	var is_counting := msg.Phase == Phase.BREEDING
	_ends_at_msec = Time.get_ticks_msec() + msg.RemainingMs
	_timer.visible = is_counting
	set_process(is_counting)

	var has_pair := msg.MotherEntityId != 0
	_fill_parents(msg.MotherEntityId, msg.FatherEntityId)
	_mother.disabled = has_pair
	_father.disabled = has_pair

	_egg.visible = msg.Egg != null
	if msg.Egg != null:
		_egg.text = BreedingText.egg_detail(msg.Egg)

	_start.visible = msg.Phase == Phase.IDLE
	_release.visible = has_pair
	_take_egg.visible = msg.Phase == Phase.EGG_READY
	_update_start()

	if is_new:
		farm_opened.emit()


## The farm's own pair when it has one; otherwise every owned bestia old enough, by sex.
func _fill_parents(mother_entity_id: int, father_entity_id: int) -> void:
	_mother.clear()
	_father.clear()
	var entity_manager := EntityManager.get_instance()
	if entity_manager == null:
		return

	for bestia in entity_manager.get_owned_bestias():
		var is_pair_member: bool = bestia.EntityId in [mother_entity_id, father_entity_id]
		var may_breed: bool = bestia.Level >= _MIN_PARENT_LEVEL and mother_entity_id == 0
		if not is_pair_member and not may_breed:
			continue

		var label := "%s  Lv. %d" % [OwnedSlots.title_of(bestia), bestia.Level]
		if bestia.Sex == _SEX_FEMALE:
			_mother.add_item(label)
			_mother.set_item_metadata(_mother.item_count - 1, bestia.EntityId)
		elif bestia.Sex == _SEX_MALE:
			_father.add_item(label)
			_father.set_item_metadata(_father.item_count - 1, bestia.EntityId)

	_select(_mother, mother_entity_id)
	_select(_father, father_entity_id)


func _select(picker: OptionButton, entity_id: int) -> void:
	for i in picker.item_count:
		if picker.get_item_metadata(i) == entity_id:
			picker.select(i)
			return


func _update_start() -> void:
	_start.disabled = _mother.selected < 0 or _father.selected < 0


func _on_start_pressed() -> void:
	if not ConnectionManager.is_ready_to_send() or _start.disabled:
		return
	ConnectionManager.start_breeding(
		_structure_id, _mother.get_item_metadata(_mother.selected), _father.get_item_metadata(_father.selected)
	)


func _on_release_pressed() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.release_breeding_pair(_structure_id)


func _on_take_egg_pressed() -> void:
	if ConnectionManager.is_ready_to_send():
		ConnectionManager.take_egg(_structure_id)


func _build() -> void:
	var body := UiStyle.vbox(8)
	add_child(UiStyle.margin(body, 12, 10, 12, 12))

	_status = UiStyle.label("", 13)
	_status.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	body.add_child(_status)
	_timer = UiStyle.label("", 16, UiStyle.ACCENT)
	body.add_child(_timer)

	_mother = _add_picker(body, tr("BREEDING_FARM_MOTHER"))
	_father = _add_picker(body, tr("BREEDING_FARM_FATHER"))

	_egg = UiStyle.label("", 12)
	body.add_child(_egg)
	body.add_child(UiStyle.spacer())

	var buttons := UiStyle.hbox(8)
	body.add_child(buttons)
	_start = _add_button(buttons, tr("BREEDING_FARM_START"), _on_start_pressed)
	_release = _add_button(buttons, tr("BREEDING_FARM_RELEASE"), _on_release_pressed)
	_take_egg = _add_button(buttons, tr("BREEDING_TAKE_EGG"), _on_take_egg_pressed)


func _add_picker(body: VBoxContainer, title: String) -> OptionButton:
	var row := UiStyle.hbox(8)
	var label := UiStyle.label(title, 13, UiStyle.MUTED)
	label.custom_minimum_size = Vector2(70, 0)
	row.add_child(label)
	var picker := OptionButton.new()
	picker.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	picker.item_selected.connect(func(_index: int) -> void: _update_start())
	row.add_child(picker)
	body.add_child(row)
	return picker


func _add_button(row: HBoxContainer, text: String, on_pressed: Callable) -> Button:
	var button := Button.new()
	button.text = text
	button.pressed.connect(on_pressed)
	row.add_child(button)
	return button
