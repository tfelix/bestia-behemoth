package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.item.equip.SlotLock
import org.springframework.stereotype.Component

/** Strip Armor: the armour goes, and cannot be put back on for a while. */
@Component
class StripArmor(losService: LineOfSightService) : Strip(losService, SlotLock.ARMOR)
