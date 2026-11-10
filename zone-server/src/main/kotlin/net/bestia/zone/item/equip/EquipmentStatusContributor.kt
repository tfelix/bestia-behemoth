package net.bestia.zone.item.equip

import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.StatusValueContributor
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStats
import org.springframework.stereotype.Component

@Component
class EquipmentStatusContributor(
  private val equipmentScriptRegistry: EquipmentScriptRegistry,
  private val itemStatCatalog: ItemStatCatalog,
  private val templates: ItemTemplateRegistry,
) : StatusValueContributor {

  override val reads: ComponentClassSet = setOf(Equipment::class)

  override fun contribute(context: StatusValueRecalcContext, world: World, id: EntityId) {
    val worn = world.get(id, Equipment::class)?.getWorn().orEmpty()
    for ((slot, item) in worn) {
      val stats = ItemStats(itemStatCatalog.sheetOf(item.itemId), item.rolledStats)
      equipmentScriptRegistry.getByItemId(item.itemId).apply(context, slot, item.upgradeLevel, stats)
    }

    // The damage formula prices refinement for the right hand only, and only the right hand can shoot.
    val rightHand = worn[EquipmentSlot.RIGHT_HAND]
    context.weaponUpgradeLevel = rightHand?.upgradeLevel ?: 0
    context.rangedWeapon = rightHand?.let { templates.templateOf(it.itemId)?.rangedWeapon }

    context.heldWeaponType = rightHand?.let { templates.templateOf(it.itemId)?.weaponType }
    context.heldTool = rightHand?.let { templates.templateOf(it.itemId)?.toolKind }
    context.offHandWeaponType = worn[EquipmentSlot.LEFT_HAND]?.let { templates.templateOf(it.itemId)?.weaponType }
    context.bodyArmorType = worn[EquipmentSlot.ARMOR]?.let { templates.templateOf(it.itemId)?.armorType }
    context.shieldArmorType = worn[EquipmentSlot.LEFT_HAND]?.let { templates.templateOf(it.itemId)?.armorType }

    context.potencyFocus += focusOf(context.heldWeaponType) + focusOf(context.offHandWeaponType)
  }

  /** A caster's tools steady the spell: Potency's Focus term, by what is held. */
  private fun focusOf(weaponType: WeaponType?): Int {
    return when (weaponType) {
      WeaponType.STAFF -> STAFF_FOCUS
      WeaponType.TOME -> TOME_FOCUS
      WeaponType.WAND -> WAND_FOCUS
      else -> 0
    }
  }

  private companion object {
    const val STAFF_FOCUS = 6
    const val TOME_FOCUS = 4
    const val WAND_FOCUS = 3
  }
}
