package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Strip Armor: the armour goes, or a wild bestia's hide guards a quarter less. */
@Component
class StripArmor(losService: LineOfSightService) : Strip(losService, EquipmentSlot.ARMOR, StatusEffectId.EXPOSED)
