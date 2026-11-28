package net.bestia.zone.faction.envoy

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.DeedObserver
import net.bestia.zone.faction.FactionService
import net.bestia.zone.faction.novitiate.Novitiates
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.FactionMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.EntityScript
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Speaking to an Envoy counts as talking to it for any quest, and asks to join its Order: a master who may
 * swear begins or continues the Novitiate there. A member of the Order needs nothing from it.
 */
@Component
class EnvoyScript(
  private val factions: FactionService,
  private val novitiates: Novitiates,
  private val deedObservers: List<DeedObserver>,
  private val jobs: AsyncJobExecutor,
) : EntityScript {

  override fun onInteract(world: World, actorId: EntityId, entityId: EntityId): OpError? {
    val envoy = world.get(entityId, FactionEnvoy::class) ?: return null
    val master = world.get(actorId, Master::class) ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    if (!isInTalkingRange(world, actorId, entityId)) {
      return OpError.TALK_OUT_OF_RANGE
    }

    deedObservers.forEach { it.onDeed(world, DeedFact(actorId, DeedFact.Deed.TALKED_TO, envoy.target)) }

    val membership = world.get(actorId, FactionMembership::class) ?: FactionMembership()
    if (membership.faction == envoy.faction) {
      return null
    }
    when (factions.refusalToSwear(world, actorId)) {
      FactionService.Refusal.ALREADY_SWORN -> return OpError.FACTION_SWORN_ELSEWHERE
      FactionService.Refusal.STILL_UNSWORN -> return OpError.FACTION_STILL_UNSWORN
      FactionService.Refusal.LEVEL_TOO_LOW -> return OpError.FACTION_LEVEL_TOO_LOW
      null -> Unit
    }

    val visitor = Novitiates.Visitor(master.masterId, accountId, actorId)
    jobs.submit(master.masterId) { novitiates.visit(visitor, envoy.faction, envoy.settlementIndex) }

    return null
  }

  private fun isInTalkingRange(world: World, actorId: EntityId, envoyId: EntityId): Boolean {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val envoyAt = world.get(envoyId, Position::class)?.toVec3L() ?: return false

    return actorAt.distance(envoyAt) <= TALK_RANGE
  }

  private companion object {
    /** The range at which townsfolk talk, see `TalkService`. */
    const val TALK_RANGE = 10L
  }
}
