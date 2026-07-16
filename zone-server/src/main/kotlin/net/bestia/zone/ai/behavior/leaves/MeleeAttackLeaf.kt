package net.bestia.zone.ai.behavior.leaves

import net.bestia.zone.ai.behavior.BtContext
import net.bestia.zone.ai.behavior.BtNode
import net.bestia.zone.ai.behavior.Status
import net.bestia.zone.ecs.battle.damage.Damage
import kotlin.random.Random

/**
 * Strikes the current target once, respecting the attack cooldown. RUNNING while on cooldown,
 * SUCCESS immediately after a strike lands, FAILURE if the target is gone.
 *
 * This is where the AI writes to a foreign entity: it stacks a [Damage] component on the target,
 * which the [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem] applies. If the target no longer
 * exists it is treated as target-lost.
 */
class MeleeAttackLeaf : BtNode {
  override fun tick(context: BtContext): Status {
    val brain = context.brain
    val targetId = brain.targetId ?: return Status.FAILURE

    if (brain.attackCooldownRemaining > 0f) {
      return Status.RUNNING
    }

    val world = context.world
    if (!world.isAlive(targetId)) {
      // Target vanished (e.g. already dead / despawned) -> treat as target lost.
      brain.targetId = null
      brain.targetPosition = null
      return Status.FAILURE
    }

    val selfId = context.entityId
    val damageAmount = Random.nextInt(MIN_DAMAGE, MAX_DAMAGE + 1)

    val damage = world.get(targetId, Damage::class) ?: world.add(targetId, Damage())
    damage.add(damageAmount, selfId)

    brain.attackCooldownRemaining = brain.attackCooldownSeconds
    return Status.SUCCESS
  }

  companion object {
    private const val MIN_DAMAGE = 1
    private const val MAX_DAMAGE = 3
  }
}
