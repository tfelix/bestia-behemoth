package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class GreaterManaPotionScript : RestorativeScript(itemId = 670L, mana = 300, isPotion = true)
