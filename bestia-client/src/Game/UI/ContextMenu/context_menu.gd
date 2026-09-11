extends PopupMenu
class_name ContextMenu

## Right-click menu for whatever is under the cursor.
##
## Populated per target rather than holding a fixed list: what you can do to another player is not what you
## can do to a crystal, and an entry that is never applicable is worse than no menu at all. A target that
## offers nothing does not open the menu - see [method open_for].

## Ids, not indices: [signal id_pressed] carries the id, and indices shift as the menu is rebuilt per target.
const _ACTION_TRADE: int = 1
const _ACTION_TALK: int = 2

var _target_entity_id: int = 0


func _ready() -> void:
	id_pressed.connect(_on_id_pressed)


## Fills the menu with what can be done to [param target] and pops it up.
##
## @return false when there is nothing on offer, in which case nothing is shown.
func open_for(target: Node3D, screen_position: Vector2) -> bool:
	clear()
	_target_entity_id = 0

	if target is MasterVisual:
		_target_entity_id = target.get_bestia_entity_id()
		add_item("Trade with %s" % target.get_master_name(), _ACTION_TRADE)

	# Offered on any creature visual, because the client cannot yet tell a townsperson from a wolf -
	# there is no NPC visual kind on the wire. The server answers nothing for a target that cannot
	# talk, so the cost of asking is a wasted message rather than a wrong menu. The proper fix is a
	# VisualKind.NPC, or a talkable flag the visual exposes.
	elif target is BestiaVisual:
		_target_entity_id = target.get_bestia_entity_id()
		add_item("Talk to", _ACTION_TALK)

	if item_count == 0:
		return false

	position = Vector2i(screen_position)
	reset_size()
	popup()

	return true


func _on_id_pressed(id: int) -> void:
	match id:
		_ACTION_TRADE:
			if _target_entity_id != 0:
				ConnectionManager.request_trade(_target_entity_id)
		_ACTION_TALK:
			if _target_entity_id != 0:
				ConnectionManager.interact(_target_entity_id)
