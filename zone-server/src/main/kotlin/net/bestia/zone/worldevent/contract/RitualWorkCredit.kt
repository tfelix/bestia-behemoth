package net.bestia.zone.worldevent.contract

import net.bestia.zone.casting.ritual.RitualObserver
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.RiftConfig
import net.bestia.zone.worldevent.rift.Rifts
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * A ritual held on a rift's ground is work for that rift: the charge it gives is what seals the gate. Every master
 * who stayed to the end is credited; a bestia helper's work is its master's own ritual and counts through them.
 */
@Component
class RitualWorkCredit(
  private val rifts: Rifts,
  private val config: RiftConfig,
  private val ledger: ContributionLedger,
) : RitualObserver {

  override fun onRitualCompleted(world: World, at: Vec3L, participants: List<EntityId>) {
    val rift = rifts.active().firstOrNull { isOnGroundOf(it.x, it.y, it.phase, at) } ?: return
    val key = ContributionLedger.Key(RIFT, rift.id)

    participants.forEach { id ->
      val master = world.get(id, Master::class) ?: return@forEach
      ledger.add(key, master.masterId, master.name, ContributionLedger.Axis.WORK, WORK_PER_RITUAL)
    }
  }

  private fun isOnGroundOf(x: Double, y: Double, phase: Phase, at: Vec3L): Boolean {
    val radius = if (phase == Phase.OPEN) config.openRadiusMetres else 2 * config.knotRadiusMetres
    return hypot(at.x - x, at.y - y) <= radius
  }

  private companion object {
    const val RIFT = "Rift"

    /** A ritual is worth three kills of a level-10 bestia: 30 seconds of channelling and a quarter of everyone's mana. */
    const val WORK_PER_RITUAL = 30.0
  }
}
