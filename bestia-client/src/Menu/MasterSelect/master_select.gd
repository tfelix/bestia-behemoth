extends Control

const EmptyMasterSlotScn = preload("res://Menu/MasterSelect/EmptyMasterSlot/EmptyMasterSlot.tscn")

@onready var _master_slots = %MasterSlots
@onready var _loading_label = %LoadingLabel

func _ready() -> void:
	_clear_master_list()
	_loading_label.show()
	ConnectionManager.connect("master_info_received", _on_master_received)
	ConnectionManager.list_bestia_master()


func _clear_master_list() -> void:
	for child in _master_slots.get_children():
		child.queue_free()


func _on_master_received(master: MasterSMSG) -> void:
	_loading_label.hide()
	_clear_master_list()

	var masters = master.Masters
	# Render one slot per available server slot: existing masters first, the
	# remainder as empty creation slots.
	var total_slots = int(max(master.MaxAvailableMasterSlots, masters.size()))

	for slot_index in range(total_slots):
		if slot_index < masters.size():
			var master_info_scene = MasterInfoScn.create(masters[slot_index])
			_master_slots.add_child(master_info_scene)
		else:
			var empty_slot = EmptyMasterSlotScn.instantiate()
			empty_slot.setup(slot_index + 1)
			_master_slots.add_child(empty_slot)
