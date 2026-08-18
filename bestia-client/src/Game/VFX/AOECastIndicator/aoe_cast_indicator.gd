extends Decal

@export var rotation_speed: float = 1.0  # Rotation speed in full rotations per second

func _ready():
	# Any initialization code can go here
	pass

func _process(delta):
	# Rotate the decal based on the rotation speed and delta time
	rotation.y += rotation_speed * 2.0 * PI * delta


## Scales the decal's ground footprint to match an AOE skill's radius.
##
## The footprint is 2 * radius + 1 tiles, not 2 * radius: server side a radius counts tiles in every
## direction *from the centre tile*, and that centre tile burns too - so a radius of 1 is the 3x3
## around the aiming point (see AreaEffectSystem). Decal.size is a full extent, and the vertical (.y)
## extent is left alone so only the ground footprint changes.
func set_radius(radius: float) -> void:
	var extent := radius * 2.0 + 1.0
	size = Vector3(extent, size.y, extent)
