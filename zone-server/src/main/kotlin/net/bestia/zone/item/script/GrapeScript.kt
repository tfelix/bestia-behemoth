package net.bestia.zone.item.script

import org.springframework.stereotype.Component

/** The mana twin of [AppleScript]: a snack, not a potion, so a Potion Pitcher cannot throw it. */
@Component
class GrapeScript : RestorativeScript(itemId = 622L, mana = 10)
