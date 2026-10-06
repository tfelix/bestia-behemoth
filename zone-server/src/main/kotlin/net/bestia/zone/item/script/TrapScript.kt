package net.bestia.zone.item.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.capture.BestiaTrap
import net.bestia.zone.capture.TrapTier
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * Sets a Magic Bestia Trap on a tile within reach; [net.bestia.zone.capture.BestiaTrapSystem] does the rest.
 *
 * Every refusal returns false, which keeps the trap in the bag. Only a master sets one, because a catch is
 * owned by a master.
 */
abstract class TrapScript(
  private val tier: TrapTier,
  private val outMessageProcessor: OutMessageProcessor,
) : ItemScript {

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val at = args.vec(ScriptArgKeys.POSITION)
    if (at == null) {
      LOG.debug { "Entity $userId used a $tier without saying where" }
      return false
    }

    val masterId = world.get(userId, Master::class)?.masterId
    val accountId = world.get(userId, Account::class)?.accountId
    if (masterId == null || accountId == null) {
      LOG.debug { "Entity $userId is not a master and cannot set a $tier" }
      return false
    }

    val userAt = world.get(userId, Position::class)?.toVec3L()
    if (userAt == null || userAt.distance(at) > RANGE_TILES) {
      return refuse(accountId, OpError.TRAP_OUT_OF_RANGE)
    }

    if (trapOn(world, at)) {
      return refuse(accountId, OpError.TRAP_NO_ROOM)
    }

    if (trapsSetBy(world, accountId) >= MAX_TRAPS) {
      return refuse(accountId, OpError.TRAP_LIMIT_REACHED, MAX_TRAPS.toString())
    }

    val trapId = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.EFFECT, TRAP_VISUAL_ID))
      add(id, BestiaTrap(ownerAccountId = accountId, masterId = masterId, trapperEntityId = userId, tier = tier))
    }

    LOG.debug { "Master $masterId set a $tier ($trapId) at $at" }

    return true
  }

  private fun trapOn(world: World, at: Vec3L): Boolean {
    var found = false
    world.query(BestiaTrap::class, Position::class).each {
      val pos = get<Position>()
      if (pos.x == at.x && pos.y == at.y) found = true
    }

    return found
  }

  private fun trapsSetBy(world: World, accountId: AccountId): Int {
    var count = 0
    world.query(BestiaTrap::class).each {
      if (get<BestiaTrap>().ownerAccountId == accountId) count++
    }

    return count
  }

  private fun refuse(accountId: AccountId, code: OpError, vararg args: String): Boolean {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code, args.toList()))

    return false
  }

  companion object {
    /** How far away a trap can be set, in tiles. The client's aiming cursor uses the same number. */
    const val RANGE_TILES = 10L

    const val MAX_TRAPS = 3

    /** The trap's entry in the client's effect catalogue (`EffectVisual/DB/2_bestia_trap.tres`). */
    const val TRAP_VISUAL_ID = 2L

    private val LOG = KotlinLogging.logger { }
  }
}
