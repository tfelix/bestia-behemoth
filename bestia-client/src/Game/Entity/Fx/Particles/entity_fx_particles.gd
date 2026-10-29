class_name EntityFxParticles extends GPUParticles3D
## A one-shot burst an [EntityFx] effect leaves where the body stood. Frees itself once the last particle is gone.


## [param colour] tints the burst, [param body_size] is the box it rises from, and [param seconds] how long the
## effect it belongs to takes.
func burst(colour: Color, body_size: Vector3, seconds: float) -> void:
	var process := process_material.duplicate() as ParticleProcessMaterial
	process.emission_box_extents = Vector3(body_size.x * 0.35, body_size.y * 0.5, body_size.z * 0.35)
	process.color = colour
	process_material = process
	# Emitted over the effect, so streaks keep leaving the body for as long as it dissolves.
	explosiveness = clampf(1.0 - seconds / lifetime, 0.0, 0.9)
	# GPU particles run on the frame clock, so they would ignore a slowed-down game.
	speed_scale = Engine.time_scale
	one_shot = true
	emitting = true
	finished.connect(queue_free)
