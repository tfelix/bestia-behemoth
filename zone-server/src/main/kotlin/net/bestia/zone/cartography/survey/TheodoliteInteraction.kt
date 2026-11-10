package net.bestia.zone.cartography.survey

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking a theodolite collects the chart its survey drew, or says how long the survey still needs. */
@Component
class TheodoliteInteraction(
  private val surveys: TheodoliteSurveys,
) : PropInteraction {

  override val kind = StaticEntityKind.THEODOLITE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val structureId = world.get(propId, PlayerStructureIdentity::class)?.structureId ?: return null
    val masterId = world.get(actorId, Master::class)?.masterId ?: world.get(actorId, OwnedBestia::class)?.masterId

    return surveys.collect(world, actorId, world.get(actorId, Account::class)?.accountId, masterId, structureId)
  }
}
