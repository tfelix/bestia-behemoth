package net.bestia.zone.poi.script

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Poi
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.DeedObserver
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.EntityScript
import net.bestia.zone.text.ReadableSMSG
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The plainest POI: a click tells the player what they are looking at. */
@Component
class ExaminePoiScript(
  private val outMessageProcessor: OutMessageProcessor,
  private val deedObservers: List<DeedObserver> = emptyList(),
) : EntityScript {

  override fun onInteract(world: World, actorId: EntityId, entityId: EntityId): OpError? {
    val poi = world.get(entityId, Poi::class) ?: return null
    val poiAt = world.get(entityId, Position::class)?.toVec3L() ?: return null
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    // The client walks into reach before it sends the click, so a click from further off is stale.
    if (actorAt.distance(poiAt) > EXAMINE_RANGE) return null

    val text = TextRef(EXAMINE_KEY, mapOf("poi" to TextArg.Token("POI_${poi.kind.name}")))
    outMessageProcessor.sendToPlayer(accountId, ReadableSMSG(entityId, text))
    val examined = DeedFact(actorId, DeedFact.Deed.EXAMINED, WorldTarget.poi(poi.kind.name, poi.instanceId))
    deedObservers.forEach { it.onDeed(world, examined) }
    return null
  }

  private companion object {
    const val EXAMINE_RANGE = 8L
    const val EXAMINE_KEY = "READABLE_POI"
  }
}
