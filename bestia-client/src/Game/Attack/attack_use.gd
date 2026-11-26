extends Object
class_name AttackUse

## Mirrors ItemUse for skills: lets a scripted skill customize the cast
## indicator/validation before MouseManager enters skill-targeting mode, or skip
## targeting for a skill that always lands on its caster (see SelfCastUse).
##
## [param max_level] is the highest level the caster knows, up to which the mouse wheel may raise
## [param level] while aiming; 0 keeps the level fixed (see MouseStateSkillTargeting.max_skill_level).
@warning_ignore("unused_parameter")
func on_skill_activated(attack: AttackResource, level: int, max_level: int = 0) -> void:
	pass
