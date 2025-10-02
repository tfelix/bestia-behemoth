extends ProgressBar

@export var color: Color = Color.WEB_GREEN

@onready var style := get("theme_override_styles/fill") as StyleBoxFlat


func _process(_delta: float) -> void:
	style.bg_color = color
