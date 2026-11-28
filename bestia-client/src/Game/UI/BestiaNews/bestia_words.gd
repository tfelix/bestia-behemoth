class_name BestiaWords

## What the zone says about an owned bestia, in words: its status line, a line of news, and the report of what it
## did while the master was away. The companion app words the same facts in lib/activity.ts, lib/diary.ts and
## lib/report.ts; keep the two close.

## News that asks the master to act, so it also shows as a notice at the top of the screen.
const PROBLEMS: Array[String] = ["WORK_PAUSED", "OUT_OF_FOOD", "ATTACKED", "RETREATED", "DIED", "LOST"]

## Less than this is not worth telling in the report.
const _NOTABLE_SECONDS := 10 * 60
const _SHOWN_GOODS := 3


static func is_problem(kind: String) -> bool:
	return kind in PROBLEMS


## "Crafting Plank 4/20 at Workbench", or "Paused: the fire is out".
static func activity_line(activity: BestiaActivityInfo) -> String:
	if activity == null or activity.Kind.is_empty():
		return ""
	if not activity.Pause.is_empty():
		return tr_of("BESTIA_PAUSED") % _keyed("BESTIA_PAUSE_", activity.Pause)

	var line := _keyed("BESTIA_DOING_", activity.Kind)
	if not activity.Item.is_empty():
		line += " " + item_name(activity.Item)
	if activity.Total > 0:
		line += " %d/%d" % [activity.Done, activity.Total]
	if not activity.Station.is_empty():
		line += " " + tr_of("BESTIA_AT") % station_name(activity.Station)
	return line


## "Brought 12 Log, 3 Resin to the Camp chest", without the bestia's name, which the caller shows beside it.
static func news_line(news: BestiaNewsSMSG) -> String:
	match news.Kind:
		"HAULED":
			var chest := news.Place if not news.Place.is_empty() else tr_of("BESTIA_THE_CHEST")
			return tr_of("BESTIA_NEWS_HAULED") % [_goods(news.Items, news.Items.size()), chest]
		"WORK_PAUSED":
			return tr_of("BESTIA_NEWS_WORK_PAUSED") % _keyed("BESTIA_STOPPED_", news.Reason)
		"QUEUE_FINISHED":
			return tr_of("BESTIA_NEWS_QUEUE_FINISHED") % station_name(news.Place)
		"NEW_ORDERS":
			return tr_of("BESTIA_NEWS_NEW_ORDERS") % _job_name(news.Reason)
		"FALLBACK_STARTED":
			return tr_of("BESTIA_NEWS_FALLBACK_STARTED") % _job_name(news.Reason)
		_:
			return _keyed("BESTIA_NEWS_", news.Kind)


## A few short sentences per bestia: how it spent the time, what it brought and made, what happened, what it does
## now, and what would get it going again.
static func report_lines(report: BestiaAwayReport, bestia_name: String) -> PackedStringArray:
	var lines := PackedStringArray()
	var spent := _time_spent(report.TimeIn)
	if not spent.is_empty():
		lines.append(tr_of("BESTIA_REPORT_SPENT") % [bestia_name, spent])
	if not report.Brought.is_empty():
		lines.append(tr_of("BESTIA_REPORT_BROUGHT") % [_goods(report.Brought, _SHOWN_GOODS), _chest_of(report)])
	for station in report.MadeAt:
		var queues: int = report.MadeAt[station]
		if queues == 1:
			lines.append(tr_of("BESTIA_REPORT_MADE_ONE") % station_name(station))
		else:
			lines.append(tr_of("BESTIA_REPORT_MADE") % [queues, station_name(station)])
	for incident in _first_of_each_kind(report.Incidents):
		lines.append(_incident_line(incident, _count_of(report.Incidents, incident.Kind)))
	lines.append(_now_line(report))
	if not report.Hint.is_empty():
		var hint := tr_of("BESTIA_HINT_" + report.Hint)
		if "%s" in hint:
			hint = hint % _chest_of(report)
		lines.append(tr_of("BESTIA_REPORT_TO_GO_ON") % hint)
	return lines


## Whether a bestia did or met anything worth a line: time alone is not news.
static func is_eventful(report: BestiaAwayReport) -> bool:
	return not report.Brought.is_empty() or not report.MadeAt.is_empty() or not report.Incidents.is_empty() \
		or not report.Blocker.is_empty()


## Items are keyed by their identifier, which is also their name key.
static func item_name(identifier: String) -> String:
	return tr_of(identifier.to_upper())


## `MINING_SHAFT` as "Mining shaft".
static func station_name(kind: String) -> String:
	var words := kind.to_lower().replace("_", " ")
	return words.substr(0, 1).to_upper() + words.substr(1)


## [method Object.tr] from a static function, where there is no object to call it on.
static func tr_of(key: String) -> String:
	return TranslationServer.translate(key)


## A key made of [param prefix] and [param name]; the name itself, lower case, when nobody wrote a text for it yet.
static func _keyed(prefix: String, name: String) -> String:
	var key := prefix + name
	var text := tr_of(key)
	return name.to_lower().replace("_", " ") if text == key else text


static func _job_name(name: String) -> String:
	if OrderVocabulary.Job.has(name):
		return OrderVocabulary.job_name(OrderVocabulary.Job[name])
	return name.to_lower()


static func _goods(items: Dictionary, shown: int) -> String:
	var names := items.keys()
	names.sort_custom(func(a: String, b: String) -> bool: return items[a] > items[b])
	var parts := PackedStringArray()
	for item in names.slice(0, shown):
		parts.append("%d %s" % [items[item], item_name(item)])
	if names.size() > shown:
		parts.append(tr_of("BESTIA_MORE"))
	return _list(parts)


static func _time_spent(time_in: Dictionary) -> String:
	var states := time_in.keys().filter(func(state: String) -> bool: return time_in[state] >= _NOTABLE_SECONDS)
	states.sort_custom(func(a: String, b: String) -> bool: return time_in[a] > time_in[b])
	var parts := PackedStringArray()
	for state in states.slice(0, 2):
		parts.append(tr_of("BESTIA_DID_" + state) % _duration(time_in[state]))
	return _list(parts)


static func _first_of_each_kind(incidents: Array) -> Array:
	var seen := {}
	var firsts := []
	for incident in incidents:
		if not seen.has(incident.Kind):
			seen[incident.Kind] = true
			firsts.append(incident)
	return firsts


static func _count_of(incidents: Array, kind: String) -> int:
	return incidents.filter(func(incident) -> bool: return incident.Kind == kind).size()


static func _incident_line(incident: BestiaIncident, count: int) -> String:
	var at := _time_of_day(incident.AtEpochMs)
	match incident.Kind:
		"ATTACKED":
			if count == 2:
				return tr_of("BESTIA_REPORT_ATTACKED_TWICE") % at
			if count > 2:
				return tr_of("BESTIA_REPORT_ATTACKED_TIMES") % [count, at]
			return tr_of("BESTIA_REPORT_ATTACKED") % at
		"WORK_PAUSED":
			return tr_of("BESTIA_REPORT_WORK_PAUSED") % [at, _keyed("BESTIA_STOPPED_", incident.Reason)]
		"FALLBACK_STARTED":
			return tr_of("BESTIA_REPORT_FALLBACK_STARTED") % [_job_name(incident.Reason).to_lower(), at]
		_:
			return tr_of("BESTIA_REPORT_" + incident.Kind) % at


static func _now_line(report: BestiaAwayReport) -> String:
	match report.Now:
		"DEAD":
			return tr_of("BESTIA_REPORT_NOW_DEAD")
		"NOT_IN_WORLD":
			return tr_of("BESTIA_REPORT_NOW_AWAY")
	var activity := BestiaActivityInfo.new()
	activity.Kind = report.Now
	activity.Station = report.NowAt
	var doing := activity_line(activity)
	return tr_of("BESTIA_REPORT_NOW") % (doing.substr(0, 1).to_lower() + doing.substr(1))


static func _chest_of(report: BestiaAwayReport) -> String:
	if report.BroughtTo.size() == 1:
		return tr_of("BESTIA_THE") % report.BroughtTo[0]
	return tr_of("BESTIA_ITS_CHEST")


static func _duration(seconds: int) -> String:
	if seconds >= 3600:
		var hours := roundi(seconds / 3600.0)
		return tr_of("BESTIA_AN_HOUR") if hours == 1 else tr_of("BESTIA_HOURS") % hours
	return tr_of("BESTIA_MINUTES") % roundi(seconds / 60.0)


## "at 5:40", in the player's own time zone.
static func _time_of_day(epoch_ms: int) -> String:
	var bias_seconds: int = Time.get_time_zone_from_system()["bias"] * 60
	var at := Time.get_datetime_dict_from_unix_time(epoch_ms / 1000 + bias_seconds)
	return tr_of("BESTIA_AT_TIME") % ("%d:%02d" % [at["hour"], at["minute"]])


## "a", "a and b", "a, b and c".
static func _list(parts: PackedStringArray) -> String:
	if parts.size() <= 1:
		return parts[0] if parts.size() == 1 else ""
	return ", ".join(parts.slice(0, parts.size() - 1)) + " " + tr_of("BESTIA_AND") + " " + parts[parts.size() - 1]
