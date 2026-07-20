class_name AttackDB

static var _instance: AttackDB = null
var _attacks := {}
const _attack_db_dir = "res://Game/Attack/DB/"

# Get or create the singleton instance
static func get_instance() -> AttackDB:
	if _instance == null:
		_instance = AttackDB.new()
		_instance._load_attacks()
	return _instance

# Initialize the singleton (call this once at game startup)
static func initialize() -> AttackDB:
	if _instance == null:
		_instance = AttackDB.new()
		_instance._load_attacks()
	return _instance

# Clear the singleton instance (useful for testing or cleanup)
static func clear_instance() -> void:
	_instance = null

func _init():
	# Private constructor - use get_instance() or initialize() instead
	pass


func _load_attacks() -> void:
	var dir := DirAccess.open(_attack_db_dir)
	var loaded_attacks_count = 0
	if dir:
		dir.list_dir_begin()
		var file_name := dir.get_next()
		while file_name != "":
			if file_name.ends_with(".tres"):
				var attack = load(_attack_db_dir + file_name)
				if attack and "skill_id" in attack:
					_attacks[attack.skill_id] = attack
					loaded_attacks_count += 1
			file_name = dir.get_next()
		dir.list_dir_end()
	print("AttackDB: Loaded %s attacks" % [loaded_attacks_count])


func get_attack(skill_id: int) -> AttackResource:
	return _attacks.get(skill_id, null)
