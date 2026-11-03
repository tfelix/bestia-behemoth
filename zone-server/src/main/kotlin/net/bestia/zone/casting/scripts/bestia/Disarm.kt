package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.stereotype.Component

/** Disarm: the weapon goes, or a wild bestia hits a fifth softer. */
@Component
class Disarm(losService: LineOfSightService) : Strip(losService, EquipmentSlot.RIGHT_HAND, StatusEffectId.WEAKENED)
