package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class SuperiorManaPotionScript : RestorativeScript(itemId = 679L, mana = 480, isPotion = true)
