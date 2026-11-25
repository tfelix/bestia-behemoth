package net.bestia.zone.casting.sense

/** The world events running right now, for the senses that feel them. On the tick. */
fun interface BrewingEvents {

  fun brewing(): List<Brewing>

  /**
   * One running event as a sense may learn of it.
   *
   * @property x where it runs, in metres
   * @property family what kind of thing it is: `mana`, `beasts`, `earth`, `sky` or `people`
   * @property eventId what exactly it is, the `world_events.yml` id
   * @property feltFrom how many times further than a small event it is felt
   * @property stage how far along it is
   * @property changesInSeconds real seconds until it moves on, such as a knot opening, or null when unknown
   */
  data class Brewing(
    val x: Double,
    val y: Double,
    val family: String,
    val eventId: String,
    val feltFrom: Double,
    val stage: Stage,
    val changesInSeconds: Long? = null,
  )

  enum class Stage { STIRRING, GATHERING, BREAKING }
}
