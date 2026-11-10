class_name PickerCastUse
extends AttackUse

## A skill that asks for a choice before it is aimed: an element, an item from the bag, a listing. A subclass opens
## its picker in [method _open_picker] and hands the choice to [method _picked] as numbers the skill's server script
## knows how to read. Closing the picker without a choice casts nothing.


func on_skill_activated(attack: AttackResource, level: int) -> void:
	if SkillCooldowns.remaining_seconds(attack.skill_id) > 0.0:
		return
	_open_picker(attack, level)


@warning_ignore("unused_parameter")
func _open_picker(attack: AttackResource, level: int) -> void:
	push_error("PickerCastUse: %s opens no picker" % attack.name)


## Aims the skill as its target type says, carrying [param args]. A skill that always lands on its caster
## overrides [method _casts_on_self] and is sent at once.
func _picked(attack: AttackResource, level: int, args: PackedInt64Array) -> void:
	if _casts_on_self():
		ConnectionManager.activate_skill(attack.skill_id, level, Vector3.ZERO, 0, args)
	else:
		MouseManager.get_instance().enter_skill_targeting(attack, level, null, args)


func _casts_on_self() -> bool:
	return false
