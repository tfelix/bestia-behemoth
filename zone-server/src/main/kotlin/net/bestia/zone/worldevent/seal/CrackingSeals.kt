package net.bestia.zone.worldevent.seal

import net.bestia.zone.casting.ritual.RitualObserver
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import net.bestia.zone.worldevent.contract.ContributionLedger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

/**
 * The seals cracking now, and which of them a ritual reinforced. A ritual held at a cracking seal reinforces it and
 * is work for its event, as one held at a rift's gate is. Rituals arrive on the tick; the event reads off it.
 */
@Component
class CrackingSeals(
  private val ledger: ContributionLedger,
) : RitualObserver {

  private val cracking = ConcurrentHashMap<Long, Vec3L>()
  private val reinforced = ConcurrentHashMap.newKeySet<Long>()

  fun watch(subjectId: Long, at: Vec3L) {
    cracking[subjectId] = at
  }

  fun forget(subjectId: Long) {
    cracking -= subjectId
    reinforced -= subjectId
  }

  fun isReinforced(subjectId: Long): Boolean = subjectId in reinforced

  override fun onRitualCompleted(world: World, at: Vec3L, participants: List<EntityId>) {
    cracking.filterValues { hypot((it.x - at.x).toDouble(), (it.y - at.y).toDouble()) <= REINFORCE_RADIUS_METRES }
      .keys
      .forEach { subjectId ->
        reinforced += subjectId
        participants.forEach { id ->
          val master = world.get(id, Master::class) ?: return@forEach
          ledger.add(ContributionLedger.Key(OLD_SEAL, subjectId), master.masterId, master.name, ContributionLedger.Axis.WORK, WORK_PER_RITUAL)
        }
      }
  }

  private companion object {
    const val OLD_SEAL = "OldSeal"

    /** A Mana Circle set up beside the seal: wide enough for a crystal in a town square. */
    const val REINFORCE_RADIUS_METRES = 60.0

    /** As a ritual at a rift's gate. */
    const val WORK_PER_RITUAL = 30.0
  }
}
