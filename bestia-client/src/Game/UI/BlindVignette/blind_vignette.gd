extends ColorRect
## Closes the screen in on the played character while it is blind, the way Ragnarok Online darkens all but a
## small circle. Follows the controlled entity like [code]buff_list.gd[/code] does.

const _BLIND_EFFECT_ID := 32

var _shown_entity_id: int = 0


func _ready() -> void:
	visible = false
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	ConnectionManager.connect("entity_received", _on_entity_received)

	var entity_manager := EntityManager.get_instance()
	if entity_manager != null:
		entity_manager.controlled_entity_changed.connect(_on_controlled_entity_changed)


func _on_controlled_entity_changed(_previous_id: int, entity_id: int) -> void:
	_shown_entity_id = entity_id

	var entity_manager := EntityManager.get_instance()
	var entity: Entity = entity_manager.get_entity(_shown_entity_id) if entity_manager else null
	_show_if_blind(entity.get_effects() if entity else [])


func _on_entity_received(msg: EntitySMSG) -> void:
	if msg is BuffListSMSG and msg.EntityId == _shown_entity_id and _shown_entity_id != 0:
		_show_if_blind(msg.Effects)


func _show_if_blind(effects: Array) -> void:
	visible = effects.any(func(effect) -> bool: return effect.EffectId == _BLIND_EFFECT_ID)
