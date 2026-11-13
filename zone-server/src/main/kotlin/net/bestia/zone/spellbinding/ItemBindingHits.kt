package net.bestia.zone.spellbinding

import net.bestia.zone.battle.ecs.damage.LandedHits
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/** Keeps the landed hits for [SpellBindingSystem], which checks the bound items both sides wear. */
@Component
class ItemBindingHits(
  private val book: SpellBindingBook,
) : LandedHits {

  private val pending = ConcurrentLinkedQueue<LandedHits.LandedHit>()

  override fun landed(hit: LandedHits.LandedHit) {
    if (book.hasItemBindings()) {
      pending.add(hit)
    }
  }

  fun drain(): List<LandedHits.LandedHit> {
    return generateSequence { pending.poll() }.toList()
  }
}
