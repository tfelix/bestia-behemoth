package net.bestia.zone.casting.essence

import net.bestia.zone.battle.ecs.damage.LandedHits
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/** Keeps the weapon blows that landed for [EssenceCastSystem], which reads what both sides wear. */
@Component
class EssenceCastHits : LandedHits {

  private val landed = ConcurrentLinkedQueue<LandedHits.LandedHit>()

  override fun landed(hit: LandedHits.LandedHit) {
    landed.add(hit)
  }

  fun drain(): List<LandedHits.LandedHit> {
    return generateSequence { landed.poll() }.toList()
  }
}
