extends Decal

@export var rotation_speed: float = 1.0  # Rotation speed in full rotations per second

func _ready():
	# Any initialization code can go here
	pass

func _process(delta):
	# Rotate the decal based on the rotation speed and delta time
	rotation.y += rotation_speed * 2.0 * PI * delta
