class_name DurationText
## A time left the way the buff bar shows it: hours ("2 h") or minutes ("13 min") rounded up, then seconds
## ("42 s") once under a minute.

const MINUTE_SECONDS := 60.0
const HOUR_SECONDS := 3600.0


static func short(seconds: float) -> String:
	if seconds > HOUR_SECONDS:
		return "%d h" % int(ceil(seconds / HOUR_SECONDS))
	if seconds > MINUTE_SECONDS:
		return "%d min" % int(ceil(seconds / MINUTE_SECONDS))
	return "%d s" % int(ceil(seconds))
