package net.bestia.zone.item.script

import org.springframework.stereotype.Component

/** The cheapest thing worth carrying: a loaf keeps you walking, it does not close a wound. */
@Component
class BreadScript : RestorativeScript(itemId = 30L, stamina = 35)
