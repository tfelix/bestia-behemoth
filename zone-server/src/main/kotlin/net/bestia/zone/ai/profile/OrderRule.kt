package net.bestia.zone.ai.profile

/**
 * One "when … then …" line of a bestia's orders. A bestia reads its rules from the top, and the first one whose
 * condition holds decides. A rule only chooses between things the job, care and courage already allow.
 */
data class OrderRule(
  val condition: Condition,
  /** The x in "stamina below x%"; null for a condition without a number. */
  val threshold: Int?,
  val action: Action,
) {

  enum class Condition(val takesThreshold: Boolean, val actions: Set<Action>) {
    STAMINA_BELOW(true, setOf(Action.EAT, Action.REST_AT_HOME)),
    HEALTH_BELOW(true, setOf(Action.HEAL, Action.RETREAT)),
    BAG_ABOVE(true, setOf(Action.HAUL_NOW)),
    NIGHT(false, setOf(Action.REST_AT_HOME, Action.KEEP_WORKING)),
    DAY(false, setOf(Action.REST_AT_HOME, Action.KEEP_WORKING)),
    STRONGER_FOE_IN_SIGHT(false, setOf(Action.RETREAT, Action.HOLD_GROUND)),
  }

  enum class Action { EAT, REST_AT_HOME, HEAL, RETREAT, HAUL_NOW, KEEP_WORKING, HOLD_GROUND }

  /** Whether this line can be followed: an action its condition allows, and a number only where it takes one. */
  val isValid: Boolean
    get() {
      val numberFits = if (condition.takesThreshold) threshold in THRESHOLDS else threshold == null

      return action in condition.actions && numberFits
    }

  companion object {
    const val MAX_RULES = 8

    val THRESHOLDS = 1..99

    fun fromNamesOrNull(condition: String, threshold: Int?, action: String): OrderRule? {
      val knownCondition = Condition.entries.firstOrNull { it.name.equals(condition, ignoreCase = true) } ?: return null
      val knownAction = Action.entries.firstOrNull { it.name.equals(action, ignoreCase = true) } ?: return null

      return OrderRule(knownCondition, threshold, knownAction)
    }
  }
}
