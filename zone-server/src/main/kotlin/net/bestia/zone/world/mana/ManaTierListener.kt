package net.bestia.zone.world.mana

import net.bestia.zone.ecs.core.World

/** Hears cells settle into a new tier. Called in a deferred block at the end of the tick, one minute's changes at once. */
fun interface ManaTierListener {

  fun onTiersChanged(world: World, changes: List<ManaTiers.Change>)
}
