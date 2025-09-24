extends Camera3D

@export var spring_arm_position: Node3D
@export var lerp_power: float = 1.0


func _process(delta: float) -> void:
	position = lerp(position, spring_arm_position.position, delta * lerp_power)
