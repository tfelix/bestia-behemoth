extends Control

## The creation screen is a scene, which SceneManager.goto_scene loads without arguments, so the slot is
## handed over through the screen's own script instead.
const CreateNewMasterScript = preload("res://Menu/CreateNewMaster/create_new_master.gd")

## Counted from 0, the way the server counts slots. The player is shown one more than this.
var _slot_index := 0

@onready var _slot_label = %SlotLabel
@onready var _highlight = %Highlight


func setup(slot_index: int) -> void:
	_slot_index = slot_index


func _ready() -> void:
	_slot_label.text = "Slot %s" % (_slot_index + 1)
	_highlight.hide()
	mouse_entered.connect(_on_mouse_entered)
	mouse_exited.connect(_on_mouse_exited)


func _on_mouse_entered() -> void:
	_highlight.show()


func _on_mouse_exited() -> void:
	_highlight.hide()


### Clicking an empty slot opens the master creation screen, to create the master in this very slot.
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		CreateNewMasterScript.slot_to_fill = _slot_index
		SceneManager.goto_scene("res://Menu/CreateNewMaster/CreateNewMaster.tscn")
