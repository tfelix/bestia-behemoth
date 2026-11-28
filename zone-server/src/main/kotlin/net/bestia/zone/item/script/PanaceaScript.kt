package net.bestia.zone.item.script

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

@Component
class PanaceaScript(
  statusEffects: StatusEffectService,
) : RestorativeScript(
  itemId = 682L,
  isPotion = true,
  cures = setOf(
    StatusEffectId.POISON,
    StatusEffectId.BLEEDING,
    StatusEffectId.BURNING,
    StatusEffectId.SICK,
    StatusEffectId.BLIND,
    StatusEffectId.CONFUSED,
    StatusEffectId.STONING,
    StatusEffectId.FROZEN,
    StatusEffectId.SLOWED,
    StatusEffectId.CRIPPLE,
    StatusEffectId.WEAKENED,
    StatusEffectId.WITHERED,
    StatusEffectId.SILENCED,
    StatusEffectId.MANA_SEALED,
    StatusEffectId.CORROSIVE,
  ),
  statusEffects = statusEffects,
)
