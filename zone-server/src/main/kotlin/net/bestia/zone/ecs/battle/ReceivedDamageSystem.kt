package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.stereotype.Component

/**
 * Distributes the
 */
@Component
class ReceivedDamageSystem : IteratingSystem(
  Damage::class
) {
  override fun update(
    deltaTime: Long,
    entity: Entity,
    zone: ZoneServer
  ) {
    val receivedDamage = entity.getOrThrow(Damage::class)
    entity.remove(Damage::class)
    if (!entity.has(TakenDamage::class)) {
      entity.add(TakenDamage())
    }
    if (entity.has(Health::class)) {
      val health = entity.getOrThrow(Health::class)
      health.current -= receivedDamage.total()

      if (health.current == 0) {
        LOG.trace { "$entity died due to damage." }
        entity.add(Dead)
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}