package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Status
import net.bestia.zone.ecs.battle.Damage
import kotlin.random.Random

/**
 * Strikes the current target once, respecting the attack cooldown. RUNNING while on cooldown,
 * SUCCESS immediately after a strike lands, FAILURE if the target is gone.
 *
 * This is the one place the AI writes to a foreign entity. It uses `withEntityWriteLock(targetId)`
 * — the exact pattern DeathSystem uses — and never nests a second foreign lock. A null result means
 * the target no longer exists, which is treated as target-lost.
 */
class MeleeAttackLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val brain = context.brain
    val targetId = brain.targetId ?: return Status.FAILURE

    if (brain.attackCooldownRemaining > 0f) {
      return Status.RUNNING
    }

    val selfId = context.entity.id
    val damage = Random.nextInt(MIN_DAMAGE, MAX_DAMAGE + 1)

    val applied = context.zone.withEntityWriteLock(targetId) { target ->
      target.getOrDefault(Damage::class) { Damage() }.add(damage, selfId)
    }

    if (applied == null) {
      // Target vanished (e.g. already dead / despawned) -> treat as target lost.
      brain.targetId = null
      brain.targetPosition = null
      return Status.FAILURE
    }

    brain.attackCooldownRemaining = brain.attackCooldownSeconds
    return Status.SUCCESS
  }

  companion object {
    private const val MIN_DAMAGE = 1
    private const val MAX_DAMAGE = 3
  }
}
