class_name BreedingText

## How bestia genetics read in the UI: sex marks, IV lines and egg progress. Shared by the profile, the party
## list, the breeding windows and the egg tooltip.

const _SEX_MARKS := {1: "♂", 2: "♀"}

## Same order as [IvReadingInfo]: STR, INT, VIT, DEX, WIL, AGI, HP, MANA, STAMINA.
const _IV_STAT_COUNT := 9

## BestiaOrigin.BESTIA_ORIGIN_BRED: an egg is always bred.
const _ORIGIN_BRED := 2


## Empty for a bestia whose sex this client was not told.
static func sex_mark(sex: int) -> String:
	return _SEX_MARKS.get(sex, "")


## One line per value: the grade, and the exact number when the master has Bloodline Study.
static func iv_lines(reading: IvReadingInfo) -> String:
	if reading == null or reading.Grades.size() < _IV_STAT_COUNT:
		return ""

	var lines: PackedStringArray = [TranslationServer.translate("IV_TITLE")]
	for i in _IV_STAT_COUNT:
		var line := "%s  %s" % [
			TranslationServer.translate("IV_STAT_%d" % i),
			TranslationServer.translate("IV_GRADE_%d" % reading.Grades[i]),
		]
		if reading.Exact.size() == _IV_STAT_COUNT:
			line += " (%d)" % reading.Exact[i]
		lines.append(line)
	return "\n".join(lines)


## "Caught by Ann", "Bred by Ann" or "Granted to Ann", from a [BestiaInfo]'s origin. Empty when it has none.
static func origin_line(origin: int, master_name: String) -> String:
	if origin <= 0 or master_name.is_empty():
		return ""
	return TranslationServer.translate("BESTIA_ORIGIN_%d" % origin) % master_name


## The origin line above the IV lines, for a bestia's tooltip.
static func bestia_tooltip(bestia: BestiaInfo) -> String:
	var parts: PackedStringArray = []
	var origin := origin_line(bestia.Origin, bestia.OriginMasterName)
	if not origin.is_empty():
		parts.append(origin)
	var ivs := iv_lines(bestia.Ivs)
	if not ivs.is_empty():
		parts.append(ivs)
	return "\n\n".join(parts)


static func species_name(mob_id: int) -> String:
	var species := BestiaDB.get_instance().get_bestia(mob_id)
	return str(TranslationServer.translate(species.name_key)) if species != null else "?"


## "Blob ♀", the one-line name of what an egg hatches into.
static func egg_title(egg: BestiaEggInfo) -> String:
	return ("%s %s" % [species_name(egg.MobId), sex_mark(egg.Sex)]).strip_edges()


static func egg_detail(egg: BestiaEggInfo) -> String:
	var percent := 0
	if egg.HatchTotalMs > 0:
		percent = int(100.0 * egg.HatchDoneMs / egg.HatchTotalMs)
	return "%s\n%s\n%s\n%s" % [
		egg_title(egg),
		origin_line(_ORIGIN_BRED, egg.BreederName),
		TranslationServer.translate("BESTIA_EGG_PROGRESS") % percent,
		iv_lines(egg.Ivs),
	]


## Hours and minutes, or minutes and seconds under an hour.
static func duration(ms: int) -> String:
	var seconds := ms / 1000
	if seconds >= 3600:
		return "%d h %02d min" % [seconds / 3600, (seconds % 3600) / 60]
	return "%d min %02d s" % [seconds / 60, seconds % 60]
