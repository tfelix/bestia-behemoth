package net.bestia.zone.battle.ecs.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.ecs.attack.AttackSystem
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.InCombat
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.logout.ecs.LogoutIntent
import org.springframework.stereotype.Component as SpringComponent
import net.bestia.zone.ecs.core.update
import net.bestia.zone.entity.ecs.Dead

/**
 * Distributes the damage to the entity. It is not yet clear if we should go this approach or rather
 * go the one that a message directly attempts to calculate the damage. However it is important to
 * handle also damage this directly came from ecs entities e.g. like AOE attacks.
 */
@SpringComponent
class ReceivedDamageSystem : System {
  override val phase = Phase.COMBAT
  override val after = setOf(AttackSystem::class)

  override val reads: ComponentClassSet = DamageGate.READS
  override val writes: ComponentClassSet =
    setOf(
      Health::class, TakenDamage::class, Dead::class, LogoutIntent::class, Casting::class, Crafting::class,
      InCombat::class, IncomingDamage::class
    )

  override fun update(world: World, deltaTime: Float) {
    world.query(IncomingDamage::class, Health::class).each { id ->
      val receivedDamage = get<IncomingDamage>()
      val hits = receivedDamage.drain()
      val health = get<Health>()

      // Removed only if still empty: the deferred queue is FIFO, so a hit that an earlier system deferred
      // this tick lands on this same instance first. Removing it unconditionally would delete that hit.
      world.defer {
        if (receivedDamage.amounts.isEmpty()) {
          world.remove(id, IncomingDamage::class)
        }
      }

      // Drained and dropped, not skipped before the drain: leaving the hits on would have the blow
      // land again on the next tick, forever. Nothing else follows either - no aggro record, no combat
      // timer - because none of it means anything to something that cannot be hurt.
      if (DamageGate.isImmune(world, id)) return@each

      val takenDamage = world.get(id, TakenDamage::class) ?: world.add(id, TakenDamage())
      hits.forEach { takenDamage.addDamage(it.sourceEntityId, it.amount) }
      takenDamage.removeOldEntries()

      val total = hits.sumOf { it.amount }
      health.current -= total

      // Taking damage aborts a pending logout and interrupts a running cast or craft. Removing the
      // component is what notifies the client (via the generic component-removed message); done inline
      // since we already hold the world rather than going through the cancel services.
      if (total > 0) {
        world.update(id, { InCombat() }) { it.remainingSeconds = InCombat.TIMEOUT_SECONDS }

        if (world.has(id, LogoutIntent::class)) {
          world.remove(id, LogoutIntent::class)
        }
        if (world.has(id, Casting::class)) {
          world.remove(id, Casting::class)
        }
        if (world.has(id, Crafting::class)) {
          world.remove(id, Crafting::class)
        }
      }

      if (health.current == 0 && Dead.markOnce(world, id)) {
        LOG.trace { "$id died due to damage." }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
