package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
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

      health.current -= receivedDamage.total()
      world.markChanged(id, Health::class)

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
