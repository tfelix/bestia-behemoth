package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.Actor

/**
 * A reason for a quest that the game can show: an open story of the history, a real shortage, a damaged roof.
 * The planner turns one seed into at most one plan.
 */
data class QuestSeed(
  val kind: Kind,
  /** What the quest is about. A seed is known by its kind and its subject. */
  val subject: Actor,
  /** Everything else the cause names, such as the site an artifact lies in. */
  val others: List<Actor> = emptyList(),
  val uniqueness: Uniqueness,
  /** What is lacking, for a seed that is about goods. */
  val need: Need? = null,
) {

  data class Need(val itemId: Long, val amount: Int)


  /** One per quest shape. Each has its motif in `quest-motifs.yml`. */
  enum class Kind {
    /** An artifact nobody holds and nobody laid to rest. */
    LOST_RELIC,

    /** A town whose stock of a good is far below what it should have. */
    TOWN_SHORTAGE,

    /** A town with destroyed buildings that asks for the material to rebuild them. */
    TOWN_DAMAGE,
  }

  enum class Uniqueness {
    /** One prize in the world. Everyone may try, the first one takes it. */
    UNIQUE,

    /** Many masters fill one counter. */
    SHARED,

    /** Each master does it alone, and the prize is made per master. */
    PERSONAL,
  }
}
