package net.bestia.zone.worldevent.contract

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import net.bestia.zone.identity.ecs.Master
import org.springframework.stereotype.Component

/** Credits everyone who earned a kill of an event creature, by its level; a boss counts five times. */
@Component
class ContributionKillObserver(
  private val ledger: ContributionLedger,
) : KillObserver {

  override val reads: ComponentClassSet = setOf(Master::class)
  override val writes: ComponentClassSet = emptySet()

  override fun onKill(world: World, kill: KillFact) {
    val event = kill.event ?: return
    val key = ContributionLedger.Key(event.event, event.subjectId)
    val points = kill.level * (if (kill.boss) BOSS_FACTOR else 1.0) / kill.creditedMasters.size

    kill.creditedMasters.forEach { entity ->
      val master = world.get(entity, Master::class) ?: return@forEach
      ledger.add(key, master.masterId, master.name, ContributionLedger.Axis.FIGHTING, points)
    }
  }

  private companion object {
    const val BOSS_FACTOR = 5.0
  }
}
