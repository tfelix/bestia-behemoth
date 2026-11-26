package net.bestia.zone.item.enchant

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Binds the Sage's endow to the weapon in a wielder's hand, and lifts it again. The worn copy and its bag copy change
 * on the tick, and the row follows in the background: the element then stays with the weapon wherever it goes.
 */
@Component
class WeaponEndowment(
  private val templates: ItemTemplateRegistry,
  private val instances: ItemInstanceRepository,
  private val asyncJobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** On the tick. False when [wielderId] holds no weapon: the endow then goes on its body as a status effect. */
  fun endow(world: World, wielderId: EntityId, element: Element, level: Int, potency: Int): Boolean {
    val (slot, weapon) = weaponOf(world, wielderId) ?: return false
    // An item holds one element at a time, so a new endow replaces the old one.
    val endow = ActiveItemEnchantment(
      ItemEnchantmentId.ENDOWED_WEAPON,
      strength = potency,
      value = element.ordinal,
      endsAt = clock.instant().plusSeconds(EndowedWeapon.SECONDS_PER_LEVEL * level),
      level = level,
    )

    show(world, wielderId, slot, weapon.copy(enchantments = withoutEndow(weapon) + endow))
    persist(world, wielderId, weapon.uniqueId) { it.enchant(endow) }

    return true
  }

  /** On the tick. Lifts an endow off the weapon in hand, as Aspersio does when it makes the weapon holy instead. */
  fun lift(world: World, wielderId: EntityId) {
    val (slot, weapon) = weaponOf(world, wielderId) ?: return
    if (weapon.enchantments.none { it.enchantment == ItemEnchantmentId.ENDOWED_WEAPON }) {
      return
    }

    show(world, wielderId, slot, weapon.copy(enchantments = withoutEndow(weapon)))
    persist(world, wielderId, weapon.uniqueId) { it.disenchant(ItemEnchantmentId.ENDOWED_WEAPON) }
  }

  private fun weaponOf(world: World, wielderId: EntityId): Pair<EquipmentSlot, Equipment.EquippedItem>? {
    val held = world.get(wielderId, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) ?: return null
    val isWeapon = templates.templateOf(held.itemId)?.weaponType != null

    return if (isWeapon) EquipmentSlot.RIGHT_HAND to held else null
  }

  private fun withoutEndow(weapon: Equipment.EquippedItem): List<ActiveItemEnchantment> {
    return weapon.enchantments.filterNot { it.enchantment == ItemEnchantmentId.ENDOWED_WEAPON }
  }

  /** The bag shows a worn copy too, so both views change, and the wielder's stats are rebuilt. */
  private fun show(world: World, wielderId: EntityId, slot: EquipmentSlot, weapon: Equipment.EquippedItem) {
    world.get(wielderId, Equipment::class)?.replace(slot, weapon)
    world.get(wielderId, Inventory::class)?.mirrorEnchantments(weapon.uniqueId, weapon.enchantments)
    world.add(wielderId, IsStatusValueDirty)
  }

  /** A mob's weapon has no row; its endow lives and ends in memory. */
  private fun persist(world: World, wielderId: EntityId, instanceId: Long, change: (ItemInstance) -> Unit) {
    val owner = ItemOwner.of(world, wielderId) ?: return

    asyncJobs.submit(key = owner.masterId) {
      transactions.executeWithoutResult {
        instances.findByIdOrNull(instanceId)?.let(change)
      }
    }
  }
}
