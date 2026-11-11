extends ItemUse
class_name SpellScrollUse

## Reading a spell scroll: its attack is aimed like the skill itself, and the scroll is then used at that target.
##
## The GDScript half of net.bestia.zone.spellscroll.SpellScrollScript, which checks the reach and refuses a bad
## target with an OperationError.


func on_item_used(_item: ItemResource) -> void:
	# A scroll is always a single instance, so the held id is its unique id.
	var unique_id := player_item_id
	SpellScrollBook.ask([unique_id], _aim.bind(unique_id))


func _aim(unique_id: int) -> void:
	var spell := SpellScrollBook.spell_on(unique_id)
	var attack := AttackDB.get_instance().get_attack(spell.x) if spell != Vector2i.ZERO else null
	var mouse := MouseManager.get_instance()
	if attack == null or mouse == null:
		printerr("SpellScrollUse: scroll %d holds no attack this client knows" % unique_id)
		return

	mouse.enter_skill_targeting(attack, spell.y, null, PackedInt64Array(), _read.bind(unique_id))


func _read(target_position: Vector3, target_entity_id: int, unique_id: int) -> void:
	if not ConnectionManager.is_ready_to_send():
		return

	var args = ConnectionManager.ScriptArgsCls.new()
	args.SetPosition(ScriptArgKeys.POSITION, target_position)
	args.SetInt(ScriptArgKeys.TARGET_ENTITY_ID, target_entity_id)
	ConnectionManager.use_item(unique_id, args)
