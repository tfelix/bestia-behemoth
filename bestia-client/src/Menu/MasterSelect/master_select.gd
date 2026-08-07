extends Control

const EmptyMasterSlotScn = preload("res://Menu/MasterSelect/EmptyMasterSlot/EmptyMasterSlot.tscn")
const DeleteMasterDialogScn = preload("res://Menu/MasterSelect/DeleteMasterDialog/DeleteMasterDialog.tscn")

# Mirrors the proto OpSuccess / OpError enum values (see operation_success.proto / operation_error.proto).
const OP_SUCCESS_MASTER_DELETED := 1
const OP_ERROR_MASTER_NOT_OWNED := 10
const OP_ERROR_MASTER_NAME_MISMATCH := 11
const OP_ERROR_MASTER_IN_USE := 12

@onready var _master_slots = %MasterSlots
@onready var _loading_label = %LoadingLabel
@onready var _status_label: Label = %StatusLabel

func _ready() -> void:
	_clear_master_list()
	_status_label.hide()
	_loading_label.show()
	ConnectionManager.connect("master_info_received", _on_master_received)
	ConnectionManager.connect("operation_success", _on_operation_success)
	ConnectionManager.connect("operation_error", _on_operation_error)
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
			master_info_scene.delete_requested.connect(_on_delete_requested)
			_master_slots.add_child(master_info_scene)
		else:
			var empty_slot = EmptyMasterSlotScn.instantiate()
			empty_slot.setup(slot_index + 1)
			_master_slots.add_child(empty_slot)


## Opens the type-the-name confirmation. The dialog is built per request rather than kept around, so it
## can never be shown holding a master that has since been deleted or re-listed.
func _on_delete_requested(master_info: MasterInfo) -> void:
	var dialog = DeleteMasterDialogScn.instantiate() as DeleteMasterDialog
	add_child(dialog)
	dialog.deletion_confirmed.connect(_on_deletion_confirmed)
	dialog.confirmed.connect(dialog.queue_free)
	dialog.canceled.connect(dialog.queue_free)
	dialog.open_for(master_info)


func _on_deletion_confirmed(master_id: int, typed_name: String) -> void:
	_show_status("Deleting %s ..." % typed_name, false)
	ConnectionManager.delete_master(master_id, typed_name)


func _on_operation_success(message) -> void:
	if message.Code != OP_SUCCESS_MASTER_DELETED:
		return

	# The server only acknowledges the deletion, so the slots are rebuilt from a fresh list rather than
	# patched locally - that also picks up the slot that just became free.
	_status_label.hide()
	_clear_master_list()
	_loading_label.show()
	ConnectionManager.list_bestia_master()


func _on_operation_error(message) -> void:
	match message.Code:
		OP_ERROR_MASTER_NOT_OWNED:
			_show_status("That character is no longer available.", true)
		OP_ERROR_MASTER_NAME_MISMATCH:
			_show_status("The name did not match. The character was not deleted.", true)
		OP_ERROR_MASTER_IN_USE:
			_show_status("That character is still in the world. Please try again in a moment.", true)
		_:
			_show_status("Could not delete the character. Please try again.", true)


func _show_status(text: String, is_error: bool) -> void:
	_status_label.text = text
	_status_label.modulate = Color(1, 0.5, 0.5) if is_error else Color(1, 1, 1)
	_status_label.show()
