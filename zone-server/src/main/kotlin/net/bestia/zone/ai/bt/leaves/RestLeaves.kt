package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ecs.movement.Path

/**
 * Lies down for at least [minSeconds], and stays down for as long as [stayAsleep] says the reason for
 * sleeping has not passed.
 *
 * This is what [Wait] could not express. A fixed wait models a nap — it ends after a set number of seconds
 * whatever the world is doing — and a creature that sleeps through the night has to keep sleeping until
 * something ends the night. Both are the same node with a different predicate, which is why the predicate is
 * a parameter rather than this leaf knowing anything about day and night: `ai/bt` is the domain-agnostic
 * tree library, and it is the bestia domain that supplies
 * `{ ctx -> BestiaDomain.isRestingPhase(ctx.memory) }`.
 *
 * Dropping the [Path] on the first tick is not tidiness. A plan step's tree replaces the previous one
 * immediately, but waypoints already handed to the movement system outlive it, so a creature that fell asleep
 * mid-amble would go on sleepwalking down its old wander path.
 *
 * There is deliberately no animation handling here. Waking up is only one of the ways sleeping ends — being
 * bitten is the other, and that discards this node without ticking it again — so anything this leaf switched
 * on when it started would be left switched on. `AiActSystem` drives the posture from whichever action is
 * *currently* being carried out instead, which cannot get stuck.
 */
class Sleep(
  private val minSeconds: Float,
  private val stayAsleep: (BtContext) -> Boolean = { false },
) : BtNode {

  init {
    require(minSeconds > 0f) { "Sleep requires minSeconds > 0, got $minSeconds" }
  }

  private var elapsed = 0f

  override fun tick(context: BtContext): Status {
    if (elapsed == 0f) {
      context.world.remove(context.entityId, Path::class)
    }

    elapsed += context.deltaTime

    return if (elapsed < minSeconds || stayAsleep(context)) Status.RUNNING else Status.SUCCESS
  }

  override fun toString(): String = "Sleep(min=${minSeconds}s)"
}
