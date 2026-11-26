package net.bestia.zone.spawn

import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.persistence.deleteAllByKind
import net.bestia.zone.spawn.persistence.MobEntityPersister
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** The mobs of an ended world do not wander into the next. Its own spawns fill it. */
@Component
class MobCarryOver(
  private val entities: PersistedEntityRepository,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    entities.deleteAllByKind(MobEntityPersister.KIND)
  }
}
