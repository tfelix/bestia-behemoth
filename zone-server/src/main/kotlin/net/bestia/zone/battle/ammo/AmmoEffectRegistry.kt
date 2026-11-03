package net.bestia.zone.battle.ammo

import org.springframework.stereotype.Component

/** Resolves an ammo item's `script` name to its [AmmoEffect] bean, the way equipment scripts are found. */
@Component
class AmmoEffectRegistry(
  effects: List<AmmoEffect>
) {

  private val byName: Map<String, AmmoEffect> = effects
    .mapNotNull { effect -> effect::class.simpleName?.let { it to effect } }
    .toMap()

  /** [AmmoEffect.NONE] for ammo without a script, and for a name no bean answers to. */
  fun effectOf(scriptName: String?): AmmoEffect {
    return scriptName?.let { byName[it] } ?: AmmoEffect.NONE
  }
}
