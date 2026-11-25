package net.bestia.zone.worldevent.rift

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.scripts.RiftSealWindow
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.springframework.stereotype.Component

/**
 * The seal window of an open rift. Once its anchor fell, the gate carries the rift's hold for 30 minutes, at the
 * rift's Potency. A Dispel that lifts it - in practice one a ritual charged - seals the rift: its mana goes into a
 * seal stone at the gate. If the window passes, the anchor rises again at the next stage.
 *
 * On the tick, in the rift system's deferred block.
 */
@Component
class RiftSeals(
  private val config: RiftConfig,
  private val statusEffects: StatusEffectService,
  private val sealStones: SealStones,
  private val residency: WorldObjectResidencyService,
  private val clock: BestiaClock,
) {

  /** @return true when [rift] was sealed just now */
  fun tend(world: World, rift: StoredRift, gate: EntityId, now: Long): Boolean {
    if (rift.phase != Phase.OPEN) return false

    if (rift.anchorSlain && rift.sealWindowEndsAtSecond == 0L) {
      statusEffects.applyEffect(world, gate, StatusEffectId.RIFT_SEAL_WINDOW, level = 1, potency = potencyOf(rift))
      rift.sealWindowEndsAtSecond = now + (RiftSealWindow.WINDOW_SECONDS * clock.speedFactor).toLong()
      return false
    }
    if (rift.sealWindowEndsAtSecond == 0L) return false

    val holds = world.get(gate, StatusEffects::class)?.hasEffect(StatusEffectId.RIFT_SEAL_WINDOW.id) == true
    return when {
      now >= rift.sealWindowEndsAtSecond -> {
        missed(world, rift, gate)
        false
      }
      !holds -> {
        seal(world, rift, gate)
        true
      }
      else -> false
    }
  }

  /** The rift's Potency against a seal, which grows with every stage it reached. */
  fun potencyOf(rift: StoredRift): Int = config.sealPotencyBase + config.sealPotencyPerStage * rift.stage

  private fun seal(world: World, rift: StoredRift, gate: EntityId) {
    val at = world.get(gate, Position::class)?.toVec3L()
    if (at != null) residency.placeNow(world, sealStones.raise(at, rift.reservoir))

    rift.reservoir = 0.0
    rift.sealWindowEndsAtSecond = 0
    rift.phase = Phase.SEALED
  }

  private fun missed(world: World, rift: StoredRift, gate: EntityId) {
    statusEffects.removeEffect(world, gate, StatusEffectId.RIFT_SEAL_WINDOW.id, RemovalReason.EXPIRED)
    rift.sealWindowEndsAtSecond = 0
    rift.anchorSlain = false
    rift.nextAnchorStage = rift.stage + 1
  }
}
