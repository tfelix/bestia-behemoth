package net.bestia.zone.casting.sense

/** Where world events are running right now, for the senses that feel them. In metres. On the tick. */
fun interface BrewingEvents {

  fun positions(): List<Pair<Double, Double>>
}
