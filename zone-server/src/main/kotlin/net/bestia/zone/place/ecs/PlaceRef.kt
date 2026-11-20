package net.bestia.zone.place.ecs

/**
 * One resolved answer to "where am I".
 *
 * A type rather than a bare `String` because [Place] compares it to decide whether anything changed.
 *
 * [settlement] is the ground of the settlement the entity stands in, so the client can tell where a town ends
 * for the building rules. Only the one settlement a player is inside, which its sign window and the map show
 * anyway; no id, so a few walks do not add up to the shape of the world map.
 */
data class PlaceRef(val name: String, val settlement: SettlementArea? = null)
