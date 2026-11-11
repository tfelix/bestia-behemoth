class_name SpellScrollBook
## Which attack each spell scroll the player has seen holds, by the scroll's unique id.
##
## The item list only names the kind, and what is written on a scroll never changes, so each scroll is asked
## about once and its answer kept for the session.

## `spell_scroll` in items.yml.
const ITEM_ID := 338

## Unique id -> Vector2i(skill id, level), or Vector2i.ZERO for a scroll the server said holds nothing.
static var _spells: Dictionary[int, Vector2i] = {}

## The requests in flight, oldest first, as [unique ids asked, callback]. The server answers one account's
## requests in order, see AccountInbox.
static var _waiting: Array[Array] = []


## Calls [param on_known] once the server has said what the scrolls in [param unique_ids] hold. At once when
## every one is known already.
static func ask(unique_ids: Array[int], on_known: Callable) -> void:
	var unknown: Array[int] = []
	for unique_id in unique_ids:
		if not _spells.has(unique_id):
			unknown.append(unique_id)
	if unknown.is_empty():
		on_known.call()
		return

	if not ConnectionManager.spell_scrolls_received.is_connected(_on_answer):
		ConnectionManager.spell_scrolls_received.connect(_on_answer)
	_waiting.append([unknown, on_known])
	ConnectionManager.request_spell_scrolls(unknown)


## The attack on the scroll as Vector2i(skill id, level), or Vector2i.ZERO for one not known to hold any.
static func spell_on(unique_id: int) -> Vector2i:
	return _spells.get(unique_id, Vector2i.ZERO)


## "Fire Bolt Lv. 3" for the scroll, or an empty text for one not known to hold an attack.
static func describe(unique_id: int) -> String:
	var spell := spell_on(unique_id)
	var attack := AttackDB.get_instance().get_attack(spell.x) if spell != Vector2i.ZERO else null
	if attack == null:
		return ""

	return TranslationServer.translate("SPELL_SCROLL_PICK") % [attack.name, spell.y]


static func _on_answer(message: SpellScrollsSMSG) -> void:
	for scroll in message.Scrolls:
		_spells[int(scroll.UniqueId)] = Vector2i(int(scroll.SkillId), scroll.Level)
	if _waiting.is_empty():
		return

	var asked: Array[int] = _waiting[0][0]
	var on_known: Callable = _waiting.pop_front()[1]
	# The answer leaves out a scroll that holds nothing; remembered as such so it is not asked about again.
	for unique_id in asked:
		_spells.get_or_add(unique_id, Vector2i.ZERO)
	if on_known.is_valid():
		on_known.call()
