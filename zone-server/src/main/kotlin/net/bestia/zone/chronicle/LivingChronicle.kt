package net.bestia.zone.chronicle

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.chronicle.persistence.WorldFact
import net.bestia.zone.chronicle.persistence.WorldFactRepository
import net.bestia.zone.world.time.BestiaClock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The history of the world as it stands today: the generated chronicle, and on top of it what happened in play.
 * Off the tick: every call reads or writes the database.
 */
@Service
class LivingChronicle(
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
    events.publishEvent(WorldFactRecordedEvent(fact.id, kind, actors))

    return fact
  }
}
