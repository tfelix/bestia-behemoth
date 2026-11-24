package net.bestia.zone.casting.essence

import net.bestia.zone.battle.ecs.damage.LandedHits
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/** Keeps who was struck for [ReflexSystem], which reads what they wear. */
@Component
class ReflexHits : LandedHits {

  private val struck = ConcurrentLinkedQueue<EntityId>()

  override fun landed(hit: LandedHits.LandedHit) {
    struck.add(hit.targetId)
  }

  fun drain(): List<EntityId> {
    return generateSequence { struck.poll() }.toList()
  }
}
