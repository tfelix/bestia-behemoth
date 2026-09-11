package net.bestia.zone.ai.knowledge

/**
 * How close to home a memory is, which is most of what decides how many people hold it.
 *
 * Not a distance. Two of these are not geographic at all: a town's own founding belongs to it whatever
 * the map says, and something famous is known in places that never saw it. Distance only separates the
 * two middle cases, and by then the question has already been answered.
 */
enum class Locality {

  /** Known everywhere, however far away and however long ago. */
  FAMOUS,

  /** This settlement is an actor on it: its founding, its walls, the year it was sacked. */
  OWN_TOWN,

  /** It happened close enough to have been seen or heard about at the time. */
  NEARBY,
}
