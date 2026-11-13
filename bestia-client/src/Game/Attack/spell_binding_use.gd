class_name SpellBindingUse
extends PickerCastUse

## Spell Binding asks, one menu after the other, which scroll to bind, the trigger, who sets it off, and then the
## delay or the item the trigger needs. A ground binding is then aimed; an item binding is cast where the Sage
## stands. The same menu releases one of the Sage's own bindings, which is free.
## The numbers are the server's, see SpellBindingChoice: [1, scroll, trigger, filter, extra] or [2, binding].

const _BIND := 1
const _RELEASE := 2

## BindingTrigger ids, and the skill level each comes with.
const _STEPS_ON := 1
const _COMES_NEAR := 2
const _AFTER_DELAY := 3
const _HOLDER_STRUCK := 4
const _HOLDER_STRIKES := 5
const _HOLDER_LOW_HP := 6
const _STRANGER_PICKS_UP := 7
const _TRIGGER_LEVELS := {
	_STEPS_ON: 1, _COMES_NEAR: 2, _AFTER_DELAY: 3, _HOLDER_STRUCK: 4, _HOLDER_STRIKES: 4, _HOLDER_LOW_HP: 5,
	_STRANGER_PICKS_UP: 5,
}
const _FILTERS := [1, 2, 3, 4]
const _DELAYS_SECONDS := [5, 30, 60, 300]


func _open_picker(attack: AttackResource, level: int) -> void:
	var scroll_ids := _held_scrolls()
	SpellScrollBook.ask(scroll_ids, func() -> void: _ask_bindings(attack, level, scroll_ids))


## The release entries need the Sage's own list, so it is asked for before the first menu opens.
func _ask_bindings(attack: AttackResource, level: int, scroll_ids: Array[int]) -> void:
	ConnectionManager.spell_bindings_received.connect(
		func(message: SpellBindingsSMSG) -> void: _show_scrolls(attack, level, scroll_ids, message.Bindings),
		CONNECT_ONE_SHOT
	)
	ConnectionManager.request_spell_bindings()


func _show_scrolls(attack: AttackResource, level: int, scroll_ids: Array[int], bindings: Array) -> void:
	var menu := PopupMenu.new()
	for unique_id in scroll_ids:
		var spell := SpellScrollBook.describe(unique_id)
		if not spell.is_empty():
			menu.add_item(spell)
			menu.set_item_metadata(menu.item_count - 1, [_BIND, unique_id])
	if menu.item_count == 0:
		menu.add_item(tr("SPELL_SCROLL_NONE"))
		menu.set_item_disabled(0, true)

	if not bindings.is_empty():
		menu.add_separator(tr("SPELL_BINDING_RELEASE"))
		for binding in bindings:
			menu.add_item(_describe_binding(binding))
			menu.set_item_metadata(menu.item_count - 1, [_RELEASE, int(binding.BindingId)])

	menu.index_pressed.connect(func(index: int) -> void: _scroll_chosen(attack, level, menu.get_item_metadata(index)))
	_show_menu(menu)


func _scroll_chosen(attack: AttackResource, level: int, choice: Array) -> void:
	if choice[0] == _RELEASE:
		ConnectionManager.activate_skill(attack.skill_id, level, Vector3.ZERO, 0, PackedInt64Array(choice))
		return
	_show_triggers(attack, level, choice[1])


func _show_triggers(attack: AttackResource, level: int, scroll_id: int) -> void:
	var menu := PopupMenu.new()
	for trigger in _TRIGGER_LEVELS:
		menu.add_item(tr("SPELL_BINDING_TRIGGER_%d" % trigger), trigger)
		menu.set_item_disabled(menu.item_count - 1, level < _TRIGGER_LEVELS[trigger])
	menu.id_pressed.connect(func(trigger: int) -> void: _show_filters(attack, level, scroll_id, trigger))
	_show_menu(menu)


func _show_filters(attack: AttackResource, level: int, scroll_id: int, trigger: int) -> void:
	var menu := PopupMenu.new()
	for filter in _FILTERS:
		menu.add_item(tr("SPELL_BINDING_FILTER_%d" % filter), filter)
	menu.id_pressed.connect(func(filter: int) -> void: _show_extra(attack, level, [_BIND, scroll_id, trigger, filter]))
	_show_menu(menu)


## The last number: the delay for "after a delay", the item for an item trigger, nothing otherwise.
func _show_extra(attack: AttackResource, level: int, chosen: Array) -> void:
	var trigger: int = chosen[2]
	if trigger == _AFTER_DELAY:
		var delays := PopupMenu.new()
		for seconds in _DELAYS_SECONDS:
			delays.add_item(tr("SPELL_BINDING_DELAY") % seconds, seconds)
		delays.id_pressed.connect(func(seconds: int) -> void: _picked(attack, level, PackedInt64Array(chosen + [seconds])))
		_show_menu(delays)
	elif trigger >= _HOLDER_STRUCK:
		_show_items(attack, level, chosen)
	else:
		_picked(attack, level, PackedInt64Array(chosen + [0]))


func _show_items(attack: AttackResource, level: int, chosen: Array) -> void:
	var menu := PopupMenu.new()
	var inventory = _inventory()
	for held in inventory.held_instances() if inventory != null else []:
		if held.item != null and held.item.item_id != SpellScrollBook.ITEM_ID:
			menu.add_item(held.title if not held.title.is_empty() else tr(held.item.name_key))
			menu.set_item_metadata(menu.item_count - 1, held.unique_id)
	if menu.item_count == 0:
		menu.add_item(tr("SPELL_BINDING_NO_ITEM"))
		menu.set_item_disabled(0, true)
	menu.index_pressed.connect(func(index: int) -> void: _bind_item(attack, level, chosen + [menu.get_item_metadata(index)]))
	_show_menu(menu)


## An item binding is not aimed: it is cast where the Sage stands, which is always within reach.
func _bind_item(attack: AttackResource, level: int, args: Array) -> void:
	var master := EntityManager.get_instance().get_master_entity()
	var here: Vector3 = master.global_position if master != null else Vector3.ZERO
	ConnectionManager.activate_skill(attack.skill_id, level, here, 0, PackedInt64Array(args))


func _describe_binding(binding) -> String:
	var attack := AttackDB.get_instance().get_attack(int(binding.SkillId))
	var name: String = attack.name if attack != null else "?"

	return tr("SPELL_BINDING_ENTRY") % [name, binding.Level, tr("SPELL_BINDING_TRIGGER_%d" % binding.Trigger)]


func _held_scrolls() -> Array[int]:
	var scroll_ids: Array[int] = []
	var inventory = _inventory()
	if inventory == null:
		return scroll_ids
	for held in inventory.held_instances():
		if held.item.item_id == SpellScrollBook.ITEM_ID:
			scroll_ids.append(held.unique_id)

	return scroll_ids


func _inventory():
	return (Engine.get_main_loop() as SceneTree).get_first_node_in_group("inventory")
