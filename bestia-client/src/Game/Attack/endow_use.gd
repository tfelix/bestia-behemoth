class_name EndowUse
extends PickerCastUse

## Endow asks which element to lay on the ally's weapon before it is aimed. The numbers are the server's Element
## ordinals, which is what the cast's arg carries.

const _ELEMENTS := [["ELEMENT_FIRE", 3], ["ELEMENT_WATER", 1], ["ELEMENT_WIND", 4], ["ELEMENT_EARTH", 2]]


func _open_picker(attack: AttackResource, level: int) -> void:
	var menu := PopupMenu.new()
	for element in _ELEMENTS:
		menu.add_item(tr(element[0]), element[1])

	menu.id_pressed.connect(func(ordinal: int) -> void: _picked(attack, level, PackedInt64Array([ordinal])))
	_show_menu(menu)
