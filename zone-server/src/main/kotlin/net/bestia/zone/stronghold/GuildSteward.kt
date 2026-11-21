package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.script.EntityScript
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a steward opens the guild storage. */
@Component
class GuildSteward(
  private val storages: GuildStorages,
) : EntityScript {

  override fun onInteract(world: World, actorId: EntityId, entityId: EntityId): OpError? {
    return storages.open(world, actorId, entityId)
  }
}
