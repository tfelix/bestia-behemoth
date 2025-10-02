package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.stereotype.Component

/**
 * Distributes the damage to the entity. It is not yet clear if we should go this approach or rather
 * go the one that a message directly attempts to calculate the damage. However it is important to
 * handle also damage this directly came from ecs entities e.g. like AOE attacks.
 */
@Component
class ReceivedDamageSystem : IteratingSystem() {
  override val requiredComponents = setOf(
    Damage::class,
    Health::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    val receivedDamage = entity.getOrThrow(Damage::class)
    entity.remove(Damage::class)

    val takenDamage = entity.getOrDefault(TakenDamage::class) { TakenDamage() }
    receivedDamage.amounts.forEach { takenDamage.addDamage(it.sourceEntityId, it.amount) }
    takenDamage.removeOldEntries()

    val health = entity.getOrThrow(Health::class)
    health.current -= receivedDamage.total()

    if (health.current == 0) {
      LOG.trace { "$entity died due to damage." }
      entity.add(Dead)
    }

    entity.add(IsDirty)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}