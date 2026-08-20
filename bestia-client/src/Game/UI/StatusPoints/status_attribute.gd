class_name StatusAttribute

## Mirrors net.bestia.zone.account.master.status.StatusAttribute on the server: these ordinals
## are exactly what InvestStatusPointCMSG carries on the wire. Appending is safe, reordering is not.
enum Attribute {
	STRENGTH = 0,
	AGILITY = 1,
	VITALITY = 2,
	INTELLIGENCE = 3,
	DEXTERITY = 4,
	WILLPOWER = 5,
}


## The key an attribute is stored under in Entity.get_status_values()'s dictionary (see
## entity.gd/update_status_values).
static func field_key(attribute: Attribute) -> String:
	match attribute:
		Attribute.STRENGTH: return "strength"
		Attribute.AGILITY: return "agility"
		Attribute.VITALITY: return "vitality"
		Attribute.INTELLIGENCE: return "intelligence"
		Attribute.DEXTERITY: return "dexterity"
		Attribute.WILLPOWER: return "willpower"
		_: return ""


static func short_code(attribute: Attribute) -> String:
	match attribute:
		Attribute.STRENGTH: return "STR"
		Attribute.AGILITY: return "AGI"
		Attribute.VITALITY: return "VIT"
		Attribute.INTELLIGENCE: return "INT"
		Attribute.DEXTERITY: return "DEX"
		Attribute.WILLPOWER: return "WIL"
		_: return "???"


## Effort points needed to raise an attribute from [param next_value] - 1 to [param next_value].
##
## Mirrors net.bestia.zone.account.master.status.EffortValueCostCalculator.stepCost on the server -
## the server re-prices every spend, so a divergence here shows up as a request the server refuses.
## The docs' effGainNeeded = max(1, nextEffValue / 3) with integer division:
## values 1-5 cost 1 each, 6-8 cost 2, 9-11 cost 3, and so on.
## See https://docs.bestia-game.net/docs/mechanics/statusvalues/#effort-values
static func step_cost(next_value: int) -> int:
	@warning_ignore("integer_division")
	return maxi(1, next_value / 3)


## Total effort points needed to reach [param target] starting from 0.
static func cumulative_cost(target: int) -> int:
	var total := 0
	for value in range(1, target + 1):
		total += step_cost(value)
	return total
