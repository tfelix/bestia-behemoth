package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class SupremeManaPotionScript : RestorativeScript(itemId = 686L, mana = 650, isPotion = true)
