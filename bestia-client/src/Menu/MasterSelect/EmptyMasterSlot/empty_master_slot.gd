extends Control

var _slot_number = 1

@onready var _slot_label = %SlotLabel

func setup(slot_number: int) -> void:
	_slot_number = slot_number


func _ready() -> void:
	_slot_label.text = "Slot %s" % _slot_number
	
