package net.bestia.zone.worldevent.rift

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.worldevent.contract.ContributionLedger
import org.springframework.stereotype.Component

/**
 * A broken rift crystal keeps the mana it drank: the rift loses that share of its reservoir, so it spills less when
 * it collapses and binds less in its seal stone. Breaking one is work for the rift, beside the fight.
 */
@Component
class RiftCrystalHarvest(
  private val rifts: Rifts,
  private val crystals: RiftCrystals,
  private val field: ManaField,
  private val config: RiftConfig,
  private val ledger: ContributionLedger,
) : KillObserver {

  override val reads: ComponentClassSet = setOf(Master::class)
  override val writes: ComponentClassSet = emptySet()

  override fun onKill(world: World, kill: KillFact) {
    val event = kill.event?.takeIf { it.event == RIFT && kill.speciesId == crystals.speciesId } ?: return
    val rift = rifts.active().firstOrNull { it.id == event.subjectId } ?: return

    // Every rift's state moves under the field's lock, see RiftSystem.
    synchronized(field) { rift.reservoir -= rift.reservoir * config.crystalShare }

    val key = ContributionLedger.Key(RIFT, rift.id)
    kill.creditedMasters.forEach { entity ->
      val master = world.get(entity, Master::class) ?: return@forEach
      ledger.add(key, master.masterId, master.name, ContributionLedger.Axis.WORK, WORK_PER_CRYSTAL / kill.creditedMasters.size)
    }
  }

  private companion object {
    const val RIFT = "Rift"

    /** A third of a sealing ritual: a crystal takes a while to break, but needs no mana. */
    const val WORK_PER_CRYSTAL = 10.0
  }
}
