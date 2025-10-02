extends RigidBody3D
class_name DamageTag

var damage_msg: DamageEntitySMSG

@onready var _damage_label: Label3D = $DamageLabel


# Modify the visuals, epending on the damage message.
func _ready() -> void:
	_damage_label.text = str(damage_msg.Damage)
