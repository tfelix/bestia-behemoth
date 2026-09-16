extends Control

@onready var _credits_label: Label = %CreditsLabel


func _ready() -> void:
	var file := FileAccess.open("res://CREDITS.txt", FileAccess.READ)
	if file:
		_credits_label.text = _format_credits(file.get_as_text())
	else:
		_credits_label.text = "No credits found."


func _on_back_button_pressed() -> void:
	SceneManager.goto_scene("res://Menu/Main/Main.tscn")


## A carriage return breaks a line in Godot just as a line feed does, so a CRLF file renders a blank
## line after every line. Drop the returns, and lay the entries out here rather than trusting the
## file's spacing: lines within an entry sit together, one blank line separates entry from entry.
func _format_credits(text: String) -> String:
	var entries := PackedStringArray()
	var entry := PackedStringArray()

	for raw_line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
		var line: String = raw_line.strip_edges()
		if line.is_empty():
			if not entry.is_empty():
				entries.append("\n".join(entry))
				entry.clear()
		else:
			entry.append(line)

	if not entry.is_empty():
		entries.append("\n".join(entry))

	return "\n\n".join(entries)
