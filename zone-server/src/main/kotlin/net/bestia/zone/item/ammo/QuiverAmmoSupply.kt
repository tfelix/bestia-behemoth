package net.bestia.zone.item.ammo

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.ammo.AmmoEffectRegistry
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The quiver and the bag behind [AmmoSupply]. Runs on the tick: the live bag goes first and the database follows
 * behind, keyed by the master like every other item write.
 */
@Component
class QuiverAmmoSupply(
  private val templates: ItemTemplateRegistry,
  private val itemStats: ItemStatCatalog,
  private val effects: AmmoEffectRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) : AmmoSupply {

  override val reads: ComponentClassSet = READS

  override val writes: ComponentClassSet = WRITES

  override fun nextFor(world: World, entityId: EntityId, weapon: RangedWeapon): Ammunition? {
    val quiver = world.get(entityId, Equipment::class)?.getQuiver() ?: return null
    val inventory = world.get(entityId, Inventory::class) ?: return null

    val itemId = quiver.firstOrNull { fits(it, weapon) && heldAmount(inventory, it) > 0 } ?: return null

    return ammunitionOf(itemId)
  }

  override fun draw(world: World, entityId: EntityId, ammunition: Ammunition): Boolean {
    return spendOne(world, entityId, ammunition.itemId)
  }

  override fun hasThrownSpare(world: World, entityId: EntityId): Boolean {
    val itemId = thrownInHand(world, entityId) ?: return false
    val inventory = world.get(entityId, Inventory::class) ?: return false

    return heldAmount(inventory, itemId) > 0
  }

  /** No attack of its own: the weapon's ATK already counts, as the weapon in hand. */
  override fun drawThrown(world: World, entityId: EntityId): Ammunition? {
    val itemId = thrownInHand(world, entityId) ?: return null
    if (!spendOne(world, entityId, itemId)) {
      return null
    }

    return Ammunition(itemId = itemId, attack = 0, element = Element.NORMAL)
  }

  private fun thrownInHand(world: World, entityId: EntityId): Long? {
    val held = world.get(entityId, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) ?: return null

    return held.itemId.takeIf { templates.templateOf(it)?.weaponType == WeaponType.THROWN }
  }

  private fun spendOne(world: World, entityId: EntityId, itemId: Long): Boolean {
    val inventory = world.get(entityId, Inventory::class) ?: return false
    if (!inventory.removeFromStack(itemId, 1)) {
      return false
    }

    val owner = ItemOwner.of(world, entityId) ?: return true
    asyncJobExecutor.submit(key = owner.masterId) {
      val spent = inventoryService.consumeAll(owner.masterId, listOf(itemId to 1), owner.playerBestiaId)
      if (!spent) {
        LOG.warn { "Master ${owner.masterId} shot ammo $itemId the database no longer holds" }
      }
    }

    return true
  }

  private fun fits(itemId: Long, weapon: RangedWeapon): Boolean {
    val template = templates.templateOf(itemId) ?: return false

    return template.isAmmo && template.ammoType == weapon.ammoType
  }

  private fun heldAmount(inventory: Inventory, itemId: Long): Int {
    return inventory.getItems().filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
  }

  private fun ammunitionOf(itemId: Long): Ammunition {
    val template = templates.templateOf(itemId)

    return Ammunition(
      itemId = itemId,
      attack = itemStats.sheetOf(itemId).attack,
      element = template?.element ?: Element.NORMAL,
      effect = effects.effectOf(template?.script)
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** The quiver, and whose bag an arrow comes out of. */
    val READS: ComponentClassSet = setOf(Equipment::class, Master::class, OwnedBestia::class)

    val WRITES: ComponentClassSet = setOf(Inventory::class)
  }
}
