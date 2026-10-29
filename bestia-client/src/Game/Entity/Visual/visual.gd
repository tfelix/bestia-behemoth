extends Node3D
class_name Visual


var ChatText = preload("res://Game/Entity/ChatText/ChatText.tscn")

const _CHAT_NODE_NAME = "ChatText"

func show_damage(_msg: DamageEntitySMSG) -> void:
	pass


func update_health(_msg: HealthComponentSMSG) -> void:
	pass


func update_animation(_msg: AnimationComponentSMSG) -> void:
	pass


## Client-side animation state, driven by [Entity] every frame it moves or comes to rest.
func update_animation_from_client(_animation_name: String) -> void:
	pass


## Drops this entity into its death pose, or picks it back up. Only player-owned entities are ever
## told this - a wild mob is destroyed on death and gets [method vanish] instead.
func set_dead(_dead: bool) -> void:
	pass


## Shows/updates the cast bar while this entity channels a skill.
func update_casting(_msg: CastingComponentSMSG) -> void:
	pass


## Hides the cast bar - the cast either completed or was interrupted.
func clear_casting() -> void:
	pass


## Shows how far along a construction site is. Only StructureVisual does anything with this.
func update_construction(_msg: ConstructionComponentSMSG) -> void:
	pass


func set_selected(_selected: bool) -> void:
	pass


## Holds the body still while an [EntityFx] effect plays, so it dissolves in one pose.
func set_frozen(frozen: bool) -> void:
	for player: AnimationPlayer in find_children("*", "AnimationPlayer", true, false):
		player.speed_scale = 0.0 if frozen else 1.0


## How far an [EntityFx] effect has dissolved the body, 0 to 1, for what the uber material does not draw.
func set_effect_fade(amount: float) -> void:
	for child in get_children():
		if child is CarriedLight:
			child.set_effect_fade(amount)


func show_chat(msg: ChatSMSG) -> void:
	var chat_anchor = get_node("ChatAnchor")

	if chat_anchor == null:
		printerr("Visual %s received chat but has not ChatAnchor set." % [name])
		return

	for x in chat_anchor.get_children():
		x.queue_free()
	var chat_text = ChatText.instantiate()
	chat_text.chat_msg = msg
	chat_text.name = _CHAT_NODE_NAME
	chat_anchor.add_child(chat_text)
