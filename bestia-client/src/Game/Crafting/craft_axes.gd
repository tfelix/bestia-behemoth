class_name CraftAxes
## Where crafting materials sit in the feature space, and how to say it in words.
##
## The axes come from the server once per connection ([code]MaterialAxesSMSG[/code]); this only stores them, so a
## tooltip anywhere can name what a material pushes a mix towards. Static, like [ItemDetail], because there is one
## feature space for the whole client.

## In the server's CraftAxis order, which is the order every axis list on the wire uses.
const AXES := ["STATE", "INTEGRITY", "HEFT", "EDGE", "VITALITY", "ARCANE", "COMPLEXITY"]

## A material is described by the axes it pushes at least this far.
const _NOTABLE := 0.3

static var _by_item_id: Dictionary = {}


static func store(axes_by_item_id: Dictionary) -> void:
	_by_item_id = axes_by_item_id


## The axes of [param item_id], one value per entry of [constant AXES], or an empty array for a non-material.
static func of(item_id: int) -> PackedFloat32Array:
	return _by_item_id.get(item_id, PackedFloat32Array())


## "fluid, restorative": the poles a material pushes towards, strongest first. Empty for a non-material.
static func describe(item_id: int) -> String:
	var axes := of(item_id)
	var notable: Array = []
	for i in axes.size():
		if absf(axes[i]) >= _NOTABLE:
			notable.append(i)
	notable.sort_custom(func(a, b): return absf(axes[a]) > absf(axes[b]))

	return ", ".join(notable.map(func(i): return pole_name(i, axes[i] > 0)))


## "too solid" for a mix that leans too far to the positive pole of STATE.
static func hint_text(axis: int, too_high: bool) -> String:
	return TranslationServer.translate("CRAFT_HINT_TOO").format({"pole": pole_name(axis, too_high)})


static func pole_name(axis: int, positive: bool) -> String:
	var key := "CRAFT_POLE_%s_%s" % [AXES[axis], "POS" if positive else "NEG"]
	return TranslationServer.translate(key)
