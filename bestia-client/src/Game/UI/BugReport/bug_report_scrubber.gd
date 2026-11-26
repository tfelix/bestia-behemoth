class_name BugReportScrubber
## Masks what could name the player, or let a reader act as them, before a report leaves this machine. The
## api-server scrubs again with the same rules (LogScrubber.kt), so keep both lists alike.

static var _rules: Array = []


static func scrub(text: String) -> String:
	if _rules.is_empty():
		_rules = [
			[_regex("(?i)\\b(Bearer|Ticket)\\s+[A-Za-z0-9._~+/=-]+"), "$1 <secret>"],
			[_regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"), "<jwt>"],
			[_regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>"],
			# Octets above 255 do not match, so most four-part driver versions survive.
			[_regex("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"), "<ip>"],
		]

	var scrubbed := text
	for rule: Array in _rules:
		scrubbed = (rule[0] as RegEx).sub(scrubbed, rule[1], true)
	return scrubbed


static func _regex(pattern: String) -> RegEx:
	var regex := RegEx.new()
	regex.compile(pattern)
	return regex
