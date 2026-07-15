extends Control

var _slot_number = 1

@onready var _slot_label = %SlotLabel
@onready var _highlight = %Highlight


func setup(slot_number: int) -> void:
	_slot_number = slot_number


func _ready() -> void:
	_slot_label.text = "Slot %s" % _slot_number
	_highlight.hide()
	mouse_entered.connect(_on_mouse_entered)
	mouse_exited.connect(_on_mouse_exited)


func _on_mouse_entered() -> void:
	_highlight.show()


func _on_mouse_exited() -> void:
	_highlight.hide()


### Clicking an empty slot opens the master creation screen.
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		SceneManager.goto_scene("res://Menu/CreateNewMaster/CreateNewMaster.tscn")
