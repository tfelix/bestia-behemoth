package net.bestia.zone.ecs.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.Casting
import net.bestia.zone.ecs.crafting.Crafting
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.InCombat
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.logout.LogoutIntent
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Distributes the damage to the entity. It is not yet clear if we should go this approach or rather
 * go the one that a message directly attempts to calculate the damage. However it is important to
 * handle also damage this directly came from ecs entities e.g. like AOE attacks.
 */
@SpringComponent
@Order(50)
class ReceivedDamageSystem : System {

  override val reads: ComponentClassSet = setOf(Damage::class)
  override val writes: ComponentClassSet =
    setOf(
      Health::class, TakenDamage::class, Dead::class, LogoutIntent::class, Casting::class, Crafting::class,
      InCombat::class
    )

  override fun update(world: World, deltaTime: Float) {
    world.query(Damage::class, Health::class).each { id ->
      val receivedDamage = get<Damage>()
      val health = get<Health>()

      world.remove(id, Damage::class)

      val takenDamage = world.get(id, TakenDamage::class) ?: world.add(id, TakenDamage())
      receivedDamage.amounts.forEach { takenDamage.addDamage(it.sourceEntityId, it.amount) }
      takenDamage.removeOldEntries()

      val total = receivedDamage.total()
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

      // Guarded rather than added unconditionally: `World.add` overwrites, and a player body stays
      // at 0 HP with its Dead component for as long as it lies there. A second drain - a simultaneous
      // attacker, a ground effect staged before it went down - would otherwise replace the component
      // and, with it, the flag saying this death has already been paid for.
      if (health.current == 0 && !world.has(id, Dead::class)) {
        LOG.trace { "$id died due to damage." }
        world.add(id, Dead())
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
