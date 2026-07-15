package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.status.Health
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
  override val writes: ComponentClassSet = setOf(Health::class, TakenDamage::class, Dead::class)

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

      // Taking damage aborts a pending logout. Removing the component is what notifies the client
      // (via the generic component-removed message); done inline since we already hold the world.
      if (total > 0 && world.has(id, LogoutIntent::class)) {
        world.remove(id, LogoutIntent::class)
      }

      if (health.current == 0) {
        LOG.trace { "$id died due to damage." }
        world.add(id, Dead)
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
