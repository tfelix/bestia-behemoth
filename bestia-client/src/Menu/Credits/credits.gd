extends Control

@onready var _credits_label: Label = %CreditsLabel


func _ready() -> void:
	var file := FileAccess.open("res://CREDITS.txt", FileAccess.READ)
	if file:
		_credits_label.text = file.get_as_text()
	else:
		_credits_label.text = "No credits found."


func _on_back_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")
