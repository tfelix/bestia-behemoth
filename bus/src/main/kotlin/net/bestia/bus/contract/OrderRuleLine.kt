package net.bestia.bus.contract

/**
 * One "when … then …" line of a bestia's orders, by name: [condition] STAMINA_BELOW, HEALTH_BELOW, BAG_ABOVE, NIGHT,
 * DAY or STRONGER_FOE_IN_SIGHT; [action] EAT, REST_AT_HOME, HEAL, RETREAT, HAUL_NOW, KEEP_WORKING or HOLD_GROUND.
 * [threshold] is the x of the first three conditions and null for the others.
 */
data class OrderRuleLine(val condition: String, val threshold: Int?, val action: String)
