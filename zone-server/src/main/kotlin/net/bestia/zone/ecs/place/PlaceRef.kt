package net.bestia.zone.ecs.place

/**
 * One resolved answer to "where am I".
 *
 * A type rather than a bare `String` because [Place] compares it to decide whether anything changed, and
 * because a place is the sort of thing that grows fields - a claim will want to say who owns it. Callers
 * get the comparison for free from the data class.
 *
 * Carries no id, no kind and no geometry, and that is the point rather than an omission. The kind is the
 * server's business: it is what decides which name wins, and a client that knew would be able to
 * second-guess it. An id would let a client accumulate the shape of the map over a few walks, which is
 * what `WeatherSMSG` keeps opaque and `WorldInfoSMSG` withholds the seed to prevent.
 */
data class PlaceRef(val name: String)
