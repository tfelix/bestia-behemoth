package net.bestia.zone.ai.domain.townsfolk

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.knowledge.KnowledgeProfile

/**
 * What one townsperson does with a day.
 *
 * An occupation belongs to a *person*, not to an archetype, which is why it is a fact in memory rather
 * than a field on an AI profile: a village's baker and its guard are the same species running the same
 * profile, and what separates them is the shop one of them keeps.
 *
 * [businessType] is the id of a `BusinessCatalogue` trade, and the join is what makes the roster the world
 * generator already decides mean something - a settlement with two bakeries wants two bakers. An
 * occupation with none is somebody a town has whether or not it has a shop for them.
 */
data class Occupation(
  val id: String,
  val label: String,
  /** A `net.bestia.worldgen.pop.BusinessType.id`, or null for a trade nobody keeps a shop for. */
  val businessType: String?,
  /** Hours at the post. Null for somebody with no work in their day at all - a child, for instance. */
  val shift: HourWindow?,
  /** Hours in bed. Its own field rather than "whatever is left", so an odd one can be checked. */
  val rest: HourWindow,
  /**
   * Whether this trade stays put while there is fighting nearby.
   *
   * Civilian by default, because almost everybody is: standing about during a brawl is the exception a
   * trade opts into. See [TownsfolkDomain.Goals.TAKE_SHELTER].
   */
  val holdsGround: Boolean = false,

  /**
   * Which of the town's news this trade tends to be the one who remembers.
   *
   * A tendency and never a guarantee - see [KnowledgeProfile]. Ordinary by default, because most trades
   * have no particular claim on any of it.
   */
  val knowledge: KnowledgeProfile = KnowledgeProfile.ORDINARY,

  /** How many ways this trade has of greeting somebody and of describing itself. */
  val dialog: OccupationDialog = OccupationDialog.ORDINARY,
)
