class_name TownsfolkVisual extends BestiaVisual

## One inhabitant of a settlement.
##
## Everything here is [BestiaVisual]'s - the name tag, the hover and select wiring, the animation
## fallbacks - except who is being drawn. Extending it rather than [Visual] is also what keeps a
## townsperson matching the `is BestiaVisual` tests in [MouseStateDefault] and [ContextMenu].

## Enough to read as a child beside an adult. Placeholder until a child body is modelled.
const _CHILD_SCALE: float = 0.72


## Named apart from [method BestiaVisual.setup_visual] rather than overriding it: that one is typed
## for [VisualComponentSMSG], and GDScript rejects an override whose signature differs.
##
## Needs no @onready child, so unlike the name tag it is safe to apply before entering the tree.
func setup_townsfolk(msg: TownsfolkVisualComponentSMSG) -> void:
	_bestia_entity_id = msg.EntityId
	set_display_name(msg.Name)
	_apply_body(msg.Body)


## Scaled rather than swapped because only the adult is modelled; a match keeps a real child body a
## branch away and off the wire.
##
## The whole visual and not $Model: the Idle and Sleep clips animate Model:scale, so anything set
## there is overwritten on the next frame. Scaling here also drops the name tag and bars to the
## right height, and the capsule keeps its feet on the ground because Model is offset upwards.
func _apply_body(body: int) -> void:
	match body:
		0: # TownsfolkBody.ADULT
			scale = Vector3.ONE
		1: # TownsfolkBody.CHILD
			scale = Vector3.ONE * _CHILD_SCALE
		_:
			printerr("TownsfolkVisual: unhandled TownsfolkBody %s, drawing an adult" % [body])
			scale = Vector3.ONE
