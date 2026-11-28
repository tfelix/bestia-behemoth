package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class SlimSupremeManaPotionScript : RestorativeScript(itemId = 690L, mana = 650, isPotion = true)
