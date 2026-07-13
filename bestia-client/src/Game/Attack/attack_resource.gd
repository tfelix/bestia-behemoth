extends Resource
class_name AttackResource

## target_type values - kept as spelled-out strings (not an int-backed enum) so they
## stay human-readable in .tres files and match the server's SkillTargetType by name,
## not by ordinal (see buildSrc/src/main/kotlin/SkillDbSyncTask.kt).
const TARGET_TYPE_GROUND := "GROUND"
const TARGET_TYPE_AOE_GROUND := "AOE_GROUND"
const TARGET_TYPE_ENEMY := "ENEMY"
const TARGET_TYPE_FRIENDLY := "FRIENDLY"

@export var skill_id: int
@export var icon: Texture2D
@export var name: String
@export var description_key: String
@export var mana_cost: int
@export var cooldown: float
@export var max_level: int = 1
@export var attack_script: GDScript
@export_enum("GROUND", "AOE_GROUND", "ENEMY", "FRIENDLY") var target_type: String = TARGET_TYPE_GROUND
@export var aoe_radius: float = 0.0

## Cache for instantiated AttackUse objects. Key: GDScript path, Value: AttackUse instance
static var _script_instance_cache: Dictionary = {}


func use_skill(level: int) -> void:
	if attack_script:
		var attack_use_instance: AttackUse = _get_or_create_attack_use_instance()
		if attack_use_instance:
			attack_use_instance.on_skill_activated(self, level)
		else:
			printerr("AttackResource: Failed to load or instantiate attack script for skill: %s" % [skill_id])
	else:
		MouseManager.enter_skill_targeting(self, level)


func _get_or_create_attack_use_instance() -> AttackUse:
	if not attack_script:
		return null

	var script_path: String = attack_script.resource_path

	if _script_instance_cache.has(script_path):
		return _script_instance_cache[script_path]

	var instance = attack_script.new()

	if not instance is AttackUse:
		printerr("AttackResource: Script at '%s' does not extend AttackUse" % [script_path])
		instance.free()
		return null

	_script_instance_cache[script_path] = instance

	return instance
