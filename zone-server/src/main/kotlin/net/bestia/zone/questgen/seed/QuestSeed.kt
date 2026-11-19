package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.Actor
import net.bestia.zone.townsfolk.rumour.RumourKind

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
  /** What a seed about news carries. */
  val news: News? = null,
) {

  /** @param rumourId the rumour as the town that tells it holds it */
  data class News(val rumourId: Long, val kind: RumourKind)

  data class Need(val itemId: Long, val amount: Int)


  /** One per quest shape. Each has its motif in `quest-motifs.yml`. */
  enum class Kind(
    /** True for a cause of the live world, which is looked at anew each round. A story of the history stays as it is. */
    val isLive: Boolean = true,
  ) {
    /** An artifact nobody holds and nobody laid to rest. */
    LOST_RELIC(isLive = false),

    /** A town whose stock of a good is far below what it should have. */
    TOWN_SHORTAGE,

    /** A town with destroyed buildings that asks for the material to rebuild them. */
    TOWN_DAMAGE,

    /** News a town has heard and a town nearby has not. The subject is the town that has not. */
    NEWS,
  }

  enum class Uniqueness {
    /** One prize in the world. Everyone may try, the first one takes it. */
    UNIQUE,

    /** Many masters fill one counter. */
    SHARED,

    /** Each master does it alone and finishes it alone. The quest goes on for the others. */
    PERSONAL,
  }
}
