package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component

/**
 * The durable name of a *player-built* static entity: the row id of its
 * [net.bestia.zone.world.prop.PlayerStructure].
 *
 * The counterpart to [WorldObjectIdentity] and deliberately not the same component. A generated prop is named
 * by where the lattice put it, which is why its name needs a `latticeVersion` to be falsifiable; a structure
 * somebody built is named by the row that records them building it, and no retune of the generator can rename
 * it. Sharing one component would have meant a nullable half on every tree in the world.
 *
 * Present only on the three station kinds, and what lets
 * [net.bestia.zone.world.prop.PlayerStructureDeathSystem] delete the row when the thing is knocked down.
 */
data class PlayerStructureIdentity(val structureId: Long) : Component
