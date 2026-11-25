package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell

/** Hears a field cell settle into a new tier. On the tick, after the field moved. */
fun interface ManaTierListener {

  fun onTierChanged(cell: Cell, from: ManaTier, to: ManaTier)
}
