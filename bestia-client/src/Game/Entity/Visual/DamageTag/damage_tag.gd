extends RigidBody3D
class_name DamageTag

var damage_msg: DamageEntitySMSG

@onready var _damage_label: Label3D = $DamageLabel


func _ready() -> void:
	_damage_label.text = _label_text()


## The outcome words are hardcoded rather than translated for the reason [code]weather.gd[/code]
## hardcodes its sky names: [code]general.csv[/code] is for sentences, and a single word belongs to
## whatever prints it.
func _label_text() -> String:
	match damage_msg.Type:
		DamageType.MISS:
			return "Miss"
		# Nothing on the server emits DODGE yet; it exists only in the wire enum. Naming it costs one
		# line and keeps an evaded swing from ever reading as a bare "0".
		DamageType.DODGE:
			return "Dodge"
		DamageType.HEAL:
			return "+%d" % damage_msg.Damage
		_:
			return str(damage_msg.Damage)
