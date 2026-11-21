package net.bestia.zone.respawn

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Where a fallen master gets up because of its guild, if anywhere. Strongholds answer it from a higher slice. */
fun interface GuildRespawnPoints {
  fun respawnPointFor(world: World, masterEntityId: EntityId): Vec3L?
}
