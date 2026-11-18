package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class ManaPotionScript : RestorativeScript(itemId = 621L, mana = 170, isPotion = true)
