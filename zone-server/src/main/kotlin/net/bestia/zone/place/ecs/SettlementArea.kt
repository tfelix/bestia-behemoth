package net.bestia.zone.place.ecs

/** The ground a settlement covers: a circle around its centre, in position units. */
data class SettlementArea(val x: Long, val y: Long, val radius: Long)
