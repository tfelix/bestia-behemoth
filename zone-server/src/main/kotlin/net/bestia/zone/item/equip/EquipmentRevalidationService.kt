package net.bestia.zone.item.equip

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.skill.NoviceGate
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.account.master.skill.MasterSkillsChangedEvent
import net.bestia.zone.util.EntityId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

/**
 * Takes off whatever a master is wearing but no longer qualifies for.
 *
 * Ragnarok Online's `pc_checkitem`: rather than letting each item subscribe to the events that might
 * invalidate it, the one rule - [EquipmentService.checkStillWearable] - is simply re-asked over the worn gear
 * whenever something that feeds it has moved. That is what makes a *declarative* restriction work. An item
 * does not have to know which of the many ways its wearer can change applies to it, and a new trigger (a
 * respec, a GM skill reset, a content edit) costs one call rather than an audit of every script.
 *
 * Invoked today from the skill investment that can end a novicehood, and once more when a master enters the
 * world, which is what catches gear that became illegal while nobody was holding it.
 *
 * ### Why it is split into phases
 *
 * [WorldView.read] and [WorldView.modify] hold the world lock for the whole block, and resolving an item
 * template is a database round trip. Doing that inside either scope would make the tick wait on I/O. So the
 * worn gear is copied out as plain values first, judged off-lock, and only the removals go back in.
 */
@Service
class EquipmentRevalidationService(
  private val world: WorldView,
  private val itemRepository: ItemRepository,
  private val equipmentService: EquipmentService,
  private val noviceGate: NoviceGate,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor
) {

  /**
   * A plain listener rather than a transactional one: [MasterSkillsChangedEvent] is already published after
   * its own transaction has committed and after the entity has been updated, so there is nothing left here
   * to wait for a commit on.
   */
  @EventListener
  fun handleMasterSkillsChanged(event: MasterSkillsChangedEvent) {
    revalidate(event.masterId, event.entityId)
  }

  fun revalidate(masterId: Long, entityId: EntityId) {
    val wearer = readWearer(entityId) ?: return

    if (wearer.worn.isEmpty()) {
      return
    }

    val templates = itemRepository.findAllById(wearer.worn.map { it.itemId }.distinct())
      .associateBy { it.id }

    val refused = wearer.worn.mapNotNull { piece ->
      val template = templates[piece.itemId] ?: return@mapNotNull null
      val denial = equipmentService.checkStillWearable(template, wearer.level, wearer.isNovice)
        ?: return@mapNotNull null

      Refused(piece, denial)
    }

    if (refused.isEmpty()) {
      return
    }

    val removed = stripOff(entityId, refused)
    if (removed.isEmpty()) {
      return
    }

    asyncJobExecutor.submit(key = masterId) {
      removed.forEach { inventoryService.unequip(masterId, playerBestiaId = null, slot = it.piece.slot) }
    }

    // One message per distinct reason, not per slot: losing the whole starter kit to one skill point is a
    // single thing that happened, and saying it three times would read as three failures.
    removed.map { it.denial }.distinct().forEach { denial ->
      outMessageProcessor.sendToPlayer(wearer.accountId, OperationErrorSMSG(denial.toOpError()))
    }

    LOG.debug { "Master $masterId lost ${removed.size} worn item(s) on revalidation: ${removed.map { it.denial }}" }
  }

  private fun readWearer(entityId: EntityId): Wearer? {
    return world.read {
      val equipment = get(entityId, Equipment::class) ?: return@read null
      val accountId = get(entityId, Account::class)?.accountId ?: return@read null

      Wearer(
        accountId = accountId,
        // Zero for an entity with no Level component, which checkStillWearable reads as unqualified rather
        // than as exempt - the same reading EquipItemHandler gives it.
        level = get(entityId, Level::class)?.level ?: 0,
        isNovice = noviceGate.isNovice(get(entityId, KnownSkills::class)),
        worn = equipment.getWorn().map { (slot, item) -> WornPiece(slot, item.itemId, item.uniqueId) }
      )
    }
  }

  private fun stripOff(entityId: EntityId, refused: List<Refused>): List<Refused> {
    return world.modify(entityId) { id ->
      val equipment = get(id, Equipment::class) ?: return@modify emptyList()
      val inventory = get(id, Inventory::class)
      val removed = mutableListOf<Refused>()

      for (entry in refused) {
        val piece = entry.piece

        // The world was unlocked while the templates were fetched, so the player may have swapped this slot
        // in the meantime. Matching on the instance and not just the slot is what stops the sweep taking off
        // whatever happens to be there now.
        if (equipment.get(piece.slot)?.uniqueId != piece.uniqueId) {
          continue
        }

        equipment.unequip(piece.slot) ?: continue
        inventory?.setEquipped(piece.uniqueId, false)
        removed.add(entry)
      }

      if (removed.isNotEmpty()) {
        add(id, IsStatusValueDirty)
      }

      removed
    } ?: emptyList()
  }

  /** What the rules need about the wearer, copied out from under the world lock. */
  private data class Wearer(
    val accountId: Long,
    val level: Int,
    val isNovice: Boolean,
    val worn: List<WornPiece>
  )

  private data class WornPiece(val slot: EquipmentSlot, val itemId: Long, val uniqueId: Long)

  private data class Refused(val piece: WornPiece, val denial: EquipmentService.Denial)

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
