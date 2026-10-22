class_name SelfCastUse
extends AttackUse

## A skill that always lands on its caster, such as a guild skill: its key casts it at once. With no entity
## picked, the server aims a friendly skill at the caster.


func on_skill_activated(attack: AttackResource, level: int) -> void:
	if SkillCooldowns.remaining_seconds(attack.skill_id) > 0.0:
		return
	ConnectionManager.activate_skill(attack.skill_id, level)
