package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.equip.SlotLock
import org.springframework.stereotype.Component

/** Disarm: the weapon goes and the hand stays empty for a while, or a wild bestia hits a fifth softer. */
@Component
class Disarm(losService: LineOfSightService) : Strip(losService, SlotLock.WEAPON, fallback = StatusEffectId.WEAKENED)
