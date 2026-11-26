package net.bestia.zone.blackboard

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A click on a blackboard: a plain one opens its window, one with an action in its bag posts or withdraws a quest.
 * The reach is checked here on the tick; the rest is database work, handed to the clicking master's job queue.
 */
@Component
class BlackboardInteraction(
  private val boards: BlackboardService,
  private val sessions: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
) : PropInteraction {

  override val kind = StaticEntityKind.BLACKBOARD

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return interact(world, actorId, propId, ScriptArgs.EMPTY)
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    if (!isInReach(world, actorId, propId)) {
      return OpError.TALK_OUT_OF_RANGE
    }
    val request = BlackboardRequest.parse(args)
    if (request == null) {
      LOG.warn { "Entity $actorId sent a blackboard request no client builds: $args" }
      return OpError.REQUEST_REFUSED
    }
    val visit = visitOf(world, actorId, propId) ?: return null

    jobs.submit(visit.masterId) { boards.handle(visit, request) }

    return null
  }

  /** The master answers for whatever it plays, so a bestia at the board posts in its master's name. */
  private fun visitOf(world: World, actorId: EntityId, boardId: EntityId): BoardVisit? {
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    val masterEntityId = sessions.getSelectedMasterEntityId(accountId)
    val master = world.get(masterEntityId, Master::class) ?: return null
    val placeName = world.get(actorId, Place::class)?.place?.name ?: ""
    val boardAt = world.get(boardId, Position::class)?.toVec3L()

    return BoardVisit(accountId, master.masterId, masterEntityId, master.name, boardId, placeName, boardAt)
  }

  private fun isInReach(world: World, actorId: EntityId, boardId: EntityId): Boolean {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val boardAt = world.get(boardId, Position::class)?.toVec3L() ?: return false

    return actorAt.distance(boardAt) <= PlayerStructureService.RANGE_TILES
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
