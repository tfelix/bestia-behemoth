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
