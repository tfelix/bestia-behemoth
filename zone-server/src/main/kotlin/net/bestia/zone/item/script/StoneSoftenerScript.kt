package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class StoneSoftenerScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 676L,
  isPotion = true,
  cures = setOf(StatusEffectId.STONING, StatusEffectId.STONE),
  statusEffects = statusEffects,
)
