extends Object
class_name AttackUse

## Mirrors ItemUse for skills: lets a scripted skill customize the cast
## indicator/validation before MouseManager enters skill-targeting mode, or skip
## targeting for a skill that always lands on its caster (see SelfCastUse).
@warning_ignore("unused_parameter")
func on_skill_activated(attack: AttackResource, level: int) -> void:
	pass
