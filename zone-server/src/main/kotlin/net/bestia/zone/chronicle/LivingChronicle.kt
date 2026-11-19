package net.bestia.zone.chronicle

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.ArtifactRecord
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.ThreadKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.chronicle.persistence.WorldFact
import net.bestia.zone.chronicle.persistence.WorldFactRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The history of the world as it stands today: the generated chronicle, and on top of it what happened in play.
 * Ask this, not the generated chronicle, where something is now or which stories are still open. Off the tick:
 * every call reads or writes the database.
 */
@Service
class LivingChronicle(
  private val worldService: WorldService,
  private val facts: WorldFactRepository,
  private val clock: BestiaClock,
  private val events: ApplicationEventPublisher,
) {

  /** The master a fact is told of. */
  data class Doer(val masterId: Long, val name: String)

  /** Adds an entry to the history. [actors] has the subject first. Joins the caller's transaction. */
  @Transactional
  fun record(kind: EventKind, actors: List<Actor>, where: Vec2d? = null, by: Doer? = null): WorldFact {
    require(actors.isNotEmpty()) { "A $kind fact needs its subject" }

    val fact = facts.save(
      WorldFact(kind, actors, where?.x, where?.y, by?.masterId, by?.name, onDay = clock.now().absoluteDay)
    )
    events.publishEvent(WorldFactRecordedEvent(fact.id, kind, actors, by))

    return fact
  }

  @Transactional(readOnly = true)
  fun whereaboutsOf(artifact: Int): ArtifactWhereabouts {
    val record = chronicle().artifacts[artifact]
    val moves = facts.findAllBySubjectTypeAndSubjectIndexOrderById(ActorType.ARTIFACT, artifact)

    return moves.asReversed().firstNotNullOfOrNull { whereaboutsAfter(it) } ?: generatedWhereabouts(record)
  }

  /** Every story still open: those the history left, without the ones play ended, and those play opened. */
  @Transactional(readOnly = true)
  fun openThreads(): List<LivingThread> {
    val chronicle = chronicle()
    val lastMove = facts.findAllBySubjectTypeOrderById(ActorType.ARTIFACT)
      .filter { whereaboutsAfter(it) != null }
      .associateBy { it.subjectIndex }

    val untouched = chronicle.threads
      .filterNot { it.kind in ARTIFACT_THREADS && it.actors.first().index in lastMove }
      .map { LivingThread(it.kind, it.actors, sinceEvent = it.since) }
    val reopened = lastMove.values.mapNotNull { move -> awayFromHome(chronicle, move) }

    return untouched + reopened
  }

  /** A moved artifact is no longer lost. It is away while its home temple stands and does not keep it. */
  private fun awayFromHome(chronicle: Chronicle, move: WorldFact): LivingThread? {
    val record = chronicle.artifacts[move.subjectIndex]
    val home = record.homeTemple.takeIf { it >= 0 && !chronicle.settlements[it].isRuin } ?: return null
    if (whereaboutsAfter(move) == ArtifactWhereabouts.KeptAt(home)) {
      return null
    }

    val actors = listOf(Actor(ActorType.ARTIFACT, record.index), Actor(ActorType.SETTLEMENT, home))
    return LivingThread(ThreadKind.ARTIFACT_AWAY, actors, sinceFact = move.id)
  }

  /** Where the artifact was after [fact], or null when the fact did not move it. */
  private fun whereaboutsAfter(fact: WorldFact): ArtifactWhereabouts? {
    val keeper = fact.actors.firstOrNull { it.type == ActorType.SETTLEMENT }
    val takerId = fact.byMasterId

    return when {
      fact.kind == EventKind.ARTIFACT_TAKEN && takerId != null ->
        ArtifactWhereabouts.WithMaster(takerId, fact.byMasterName ?: "")

      fact.kind == EventKind.ARTIFACT_ENSHRINED && keeper != null -> ArtifactWhereabouts.KeptAt(keeper.index)
      else -> null
    }
  }

  private fun generatedWhereabouts(record: ArtifactRecord): ArtifactWhereabouts {
    return when {
      record.holder >= 0 -> ArtifactWhereabouts.WithFigure(record.holder)
      record.keptAt >= 0 -> ArtifactWhereabouts.KeptAt(record.keptAt)
      record.restingSite >= 0 -> ArtifactWhereabouts.AtSite(record.restingSite)
      else -> ArtifactWhereabouts.Unknown
    }
  }

  private fun chronicle(): Chronicle {
    return worldService.generated.world.chronicle
  }

  private companion object {
    val ARTIFACT_THREADS = setOf(ThreadKind.ARTIFACT_LOST, ThreadKind.ARTIFACT_AWAY)
  }
}
