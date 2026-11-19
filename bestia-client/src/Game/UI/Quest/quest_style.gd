class_name QuestStyle
## How every quest window words and colours a quest: the board, the log and the tracker show it the same way.
##
## Static helpers, so they go through [TranslationServer] rather than [method Object.tr].

## By the band [QuestFormatter] rates a quest's suggested level in, as players know it from other games.
const BAND_COLOURS := {
	"trivial": Color(0.60, 0.60, 0.62),
	"easy": Color(0.45, 0.85, 0.45),
	"fair": Color(1.0, 0.82, 0.35),
	"hard": Color(1.0, 0.55, 0.25),
	"deadly": Color(0.90, 0.38, 0.38),
}

const ENDED_STATUSES := ["completed", "expired", "withdrawn", "superseded"]

static var _formatter := QuestFormatter.new()


static func title_of(quest) -> String:
	return DialogText.resolve_ref(quest.Title)


## The longer text under the title: the title key's [code]_TEXT[/code] twin, with the poster and the pay added to
## the quest's own args. Empty when the twin has no row.
static func text_of(quest) -> String:
	var key: String = quest.Title.Key.replace("_TITLE", "_TEXT")
	if TranslationServer.translate(key) == key:
		return ""

	# Untyped, so plain dictionaries can join the quest's typed args: DialogText reads both the same way.
	var args: Array = []
	args.append_array(quest.Title.Args)
	args.append({"Name": "poster", "KindName": "name", "Text": quest.PosterName})
	var pay := 0
	for reward in quest.Rewards:
		pay = reward.Amount
	args.append({"Name": "pay", "KindName": "number", "Number": pay})
	return DialogText.resolve_line(key, args)


static func time_left(quest) -> String:
	var now := int(Time.get_unix_time_from_system() * 1000.0)
	var unit := _formatter.TimeLeftUnit(quest.DeadlineEpochMs, now)
	if unit == "ended":
		return TranslationServer.translate("QUEST_TIME_LEFT_ENDED")
	return TranslationServer.translate("QUEST_TIME_LEFT_%s" % unit.to_upper()) % _formatter.TimeLeftAmount(quest.DeadlineEpochMs, now)


static func level_colour(suggested_level: int) -> Color:
	var manager := EntityManager.get_instance()
	var master := manager.get_master_entity() if manager != null else null
	var own_level := master.get_level() if master != null else suggested_level
	return BAND_COLOURS[_formatter.LevelBand(suggested_level, own_level)]


static func has_ended(quest) -> bool:
	return quest.StatusName in ENDED_STATUSES


static func board_fee(pay: int, percent: int) -> int:
	return _formatter.BoardFee(pay, percent)
