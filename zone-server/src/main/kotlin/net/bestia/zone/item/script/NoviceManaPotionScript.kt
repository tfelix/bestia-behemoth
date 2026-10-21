package net.bestia.zone.item.script

import org.springframework.stereotype.Component

@Component
class NoviceManaPotionScript : RestorativeScript(itemId = 70L, mana = 50)
