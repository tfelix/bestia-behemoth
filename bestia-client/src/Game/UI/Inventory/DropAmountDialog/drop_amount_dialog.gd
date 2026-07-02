extends ConfirmationDialog
class_name DropAmountDialog

signal amount_confirmed(item_id: int, amount: int)

@onready var _spin_box: SpinBox = %AmountSpinBox

var _item_id: int = 0


func open_for(item: ItemResource, max_amount: int) -> void:
	_item_id = item.item_id
	_spin_box.min_value = 1
	_spin_box.max_value = max_amount
	_spin_box.value = max_amount
	dialog_text = "Drop how many %s?" % tr(item.name_key)
	popup_centered()


func _on_confirmed() -> void:
	amount_confirmed.emit(_item_id, int(_spin_box.value))
