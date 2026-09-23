package net.bestia.zone.item.script

import org.springframework.stereotype.Component

/** The twin of [SmallHealthPotionScript], and the reason a caster has anything to buy in a village. */
@Component
class SmallManaPotionScript : RestorativeScript(itemId = 33L, mana = 45)
