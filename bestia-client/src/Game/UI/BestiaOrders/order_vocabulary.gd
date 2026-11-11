class_name OrderVocabulary

## What orders may say, as zone-server accepts them: the proto ordinals of SetBestiaAiConfigCMSG, which jobs take a
## fallback, the limits of each Fine-tune number and which actions each rule condition allows. The server checks
## all of it again; this only keeps the window from offering what it would refuse.

enum Job { UNSET, GUARD, REST, GATHER, CRAFT, MINE }
enum Condition { UNSET, STAMINA_BELOW, HEALTH_BELOW, BAG_ABOVE, NIGHT, DAY, STRONGER_FOE_IN_SIGHT }
enum Action { UNSET, EAT, REST_AT_HOME, HEAL, RETREAT, HAUL_NOW, KEEP_WORKING, HOLD_GROUND }

## The PlaceKind ordinals of order_book_smsg.proto.
const PLACE_AREA := 1
const PLACE_CHEST := 2

const JOBS: Array[int] = [Job.GUARD, Job.GATHER, Job.CRAFT, Job.MINE, Job.REST]
## TIMID to BRAVE.
const COURAGE_STEPS: Array[int] = [1, 2, 3, 4, 5]

const MAX_RULES := 8
const THRESHOLD_MIN := 1
const THRESHOLD_MAX := 99
const NEW_RULE_THRESHOLD := 30

## Keyed like OrderSheet.Overrides, in the order Fine-tune lists them.
const NUMBERS := {
	"retreat_below_health_pct": Vector2i(0, 90),
	"eat_below_stamina_pct": Vector2i(10, 90),
	"heal_below_health_pct": Vector2i(10, 90),
	"haul_at_load_pct": Vector2i(30, 100),
	"area_radius": Vector2i(4, 64),
}

const _ACTIONS_OF := {
	Condition.STAMINA_BELOW: [Action.EAT, Action.REST_AT_HOME],
	Condition.HEALTH_BELOW: [Action.HEAL, Action.RETREAT],
	Condition.BAG_ABOVE: [Action.HAUL_NOW],
	Condition.NIGHT: [Action.REST_AT_HOME, Action.KEEP_WORKING],
	Condition.DAY: [Action.REST_AT_HOME, Action.KEEP_WORKING],
	Condition.STRONGER_FOE_IN_SIGHT: [Action.RETREAT, Action.HOLD_GROUND],
}

const _TAKES_THRESHOLD: Array[int] = [Condition.STAMINA_BELOW, Condition.HEALTH_BELOW, Condition.BAG_ABOVE]
const _TAKES_FALLBACK: Array[int] = [Job.GATHER, Job.CRAFT, Job.MINE]


## What [param job] may fall back to while it has nothing to do: any other working job. Empty when it takes none.
static func fallbacks_for(job: int) -> Array[int]:
	var fallbacks: Array[int] = []
	if job not in _TAKES_FALLBACK:
		return fallbacks
	for other in [Job.GUARD, Job.GATHER, Job.CRAFT, Job.MINE]:
		if other != job:
			fallbacks.append(other)
	return fallbacks


static func conditions() -> Array:
	return _ACTIONS_OF.keys()


static func actions_for(condition: int) -> Array:
	return _ACTIONS_OF.get(condition, [])


static func takes_threshold(condition: int) -> bool:
	return condition in _TAKES_THRESHOLD


## A rule line whose condition changed keeps a fitting action, and a number only where one is taken.
static func fitted(rule: Dictionary) -> Dictionary:
	var condition: int = rule["condition"]
	var actions := actions_for(condition)
	var action: int = rule["action"] if rule["action"] in actions else actions[0]
	var threshold := -1
	if takes_threshold(condition):
		threshold = rule["threshold"] if rule["threshold"] >= 0 else NEW_RULE_THRESHOLD
	return {"condition": condition, "action": action, "threshold": threshold}


static func new_rule() -> Dictionary:
	return {"condition": Condition.STAMINA_BELOW, "action": Action.EAT, "threshold": NEW_RULE_THRESHOLD}


static func job_name(job: int) -> String:
	return TranslationServer.translate("ORDERS_JOB_%d" % job)


static func courage_name(step: int) -> String:
	return TranslationServer.translate("ORDERS_COURAGE_%d" % step)
