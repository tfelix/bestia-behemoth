package net.bestia.zone.item.container

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.persistence.ContainerSlotRepository
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemContainerRepository
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.LootItemEntityPersister
import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.persistence.deleteAllByKind
import net.bestia.zone.world.WorldRecreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Into a new world, masters and bestia take only the gear they wear. Everything else goes: the rest of their bags,
 * guild storage, items on the ground, and whatever the slices that held items for players had left.
 */
@Component
class ItemCarryOver(
  private val slots: ContainerSlotRepository,
  private val instances: ItemInstanceRepository,
  private val containers: ItemContainerRepository,
  private val guildStorages: GuildStorageRepository,
  private val entities: PersistedEntityRepository,
  private val instanceDependents: List<ItemInstanceDependents>,
) {

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.CARRY_OVER_ITEMS)
  @Transactional
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    if (!event.isRebirth) {
      return
    }
    guildStorages.deleteAll()
    entities.deleteAllByKind(LootItemEntityPersister.KIND)
    guildStorages.flush()

    val slotsDropped = slots.deleteAllButWornBy(WEARERS)
    containers.clearAllQuivers()

    // Instances outlive their slots, so they go once nothing holds them: and first whatever is keyed by them.
    val orphans = instances.findIdsInNoSlot()
    instanceDependents.forEach { it.deleteFor(orphans) }
    instances.deleteAllById(orphans)

    LOG.warn { "Carry-over: dropped $slotsDropped item slot(s) and ${orphans.size} item instance(s)" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    val WEARERS = listOf(ItemContainer.Type.MASTER, ItemContainer.Type.BESTIA)
  }
}
