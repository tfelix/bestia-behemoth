extends Resource
class_name AttackResource

## target_type values - kept as spelled-out strings (not an int-backed enum) so they
## stay human-readable in .tres files and match the server's SkillTargetType by name,
## not by ordinal (see buildSrc/src/main/kotlin/SkillDbSyncTask.kt).
const TARGET_TYPE_GROUND := "GROUND"
const TARGET_TYPE_AOE_GROUND := "AOE_GROUND"
const TARGET_TYPE_ENEMY := "ENEMY"
const TARGET_TYPE_FRIENDLY := "FRIENDLY"

## Stand-in for a skill whose art is not authored yet. [member icon] is deliberately left unset in the
## .tres until then (SkillDbSyncTask never writes the field), and every read goes through
## [method get_icon] so an unfinished skill draws something visible instead of an empty box.
const MISSING_ICON: Texture2D = preload("res://Game/UI/Inventory/InventoryItem/item_placeholder.png")

@export var skill_id: int
@export var icon: Texture2D
@export var name: String
@export var description_key: String
@export var mana_cost: int
@export var cooldown: float
## Seconds the caster channels before the skill resolves; 0 means instant. Mirrors the server's
## castTime (skills.yml) and is synced by SkillDbSyncTask. The bar itself is driven by the server's
## Casting component, so this is presentation/prediction only.
@export var cast_time: float = 0.0
@export var max_level: int = 1
@export var attack_script: GDScript
@export_enum("GROUND", "AOE_GROUND", "ENEMY", "FRIENDLY") var target_type: String = TARGET_TYPE_GROUND
@export var aoe_radius: float = 0.0

## Which skill tree, and inside it which sub-tree, the Skills window groups this skill under. Mirrors
## master_skill_tree.yml and is synced by SkillDbSyncTask; both are empty for a skill outside the master
## tree entirely (a bestia or item-taught one), which the window collects into a tab of its own.
@export var tree: String = ""
@export var sub_tree: String = ""

## True for a skills.yml SkillType.PASSIVE - an always-on effect the master only invests points into,
## never casts. The server has no cast path for one at all (SkillStrategyFactory throws on a PASSIVE),
## so the client must not offer it either: a passive can't be dragged onto the hotbar and can't be
## activated. Synced by SkillDbSyncTask.
@export var is_passive: bool = false

## Cache for instantiated AttackUse objects. Key: GDScript path, Value: AttackUse instance
static var _script_instance_cache: Dictionary = {}


## The texture to draw for this skill. Always prefer this over reading [member icon] directly, so the
## not-authored-yet fallback lives in exactly one place rather than at each of the call sites.
func get_icon() -> Texture2D:
	return icon if icon != null else MISSING_ICON


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
