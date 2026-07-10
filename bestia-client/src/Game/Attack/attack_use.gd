extends Object
class_name AttackUse

## Mirrors ItemUse for skills: lets a scripted skill customize the cast
## indicator/validation before MouseManager enters skill-targeting mode.
## Unlike items, skills always need a target/confirm step, so there is no
## flat fire-and-forget fallback - see AttackResource.use_skill().
@warning_ignore("unused_parameter")
func on_skill_activated(attack: AttackResource, level: int) -> void:
	pass
