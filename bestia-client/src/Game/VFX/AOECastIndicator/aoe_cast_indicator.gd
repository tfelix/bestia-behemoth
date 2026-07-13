extends Decal

@export var rotation_speed: float = 1.0  # Rotation speed in full rotations per second

func _ready():
	# Any initialization code can go here
	pass

func _process(delta):
	# Rotate the decal based on the rotation speed and delta time
	rotation.y += rotation_speed * 2.0 * PI * delta


## Scales the decal's ground footprint to match an AOE skill's radius. Decal.size is a
## full extent, not a radius, so this doubles it; the vertical (.y) extent is left
## untouched so only the footprint on the ground changes.
func set_radius(radius: float) -> void:
	size = Vector3(radius * 2.0, size.y, radius * 2.0)
