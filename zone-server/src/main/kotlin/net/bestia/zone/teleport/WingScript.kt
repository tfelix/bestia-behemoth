package net.bestia.zone.teleport

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.logout.ecs.LogoutIntent
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.trade.ecs.Trading
import net.bestia.zone.travel.Journey
import net.bestia.zone.util.EntityId

/**
 * What both wings share: the refusals, the lock against chaining them, and the vanish. Where the body goes is
 * the subclass's, and [TeleportSystem] moves it once the vanish has played.
 *
 * Only the body that uses a wing goes. A master's bestias stay where they are, and a bestia can carry and use a
 * wing of its own.
 */
abstract class WingScript(
  private val statusEffects: StatusEffectService,
  private val out: OutMessageProcessor,
) : ItemScript {

  protected abstract val vanish: EntityFx.Kind
  protected abstract val vanishMs: Int

  protected abstract fun destination(world: World, userId: EntityId): Teleporting.Destination

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    if (isBusy(world, userId)) {
      return refuse(world, userId, OpError.TELEPORT_NOT_NOW)
    }
    if (world.get(userId, StatusEffects::class)?.hasEffect(StatusEffectId.TELEPORT_LOCK.id) == true) {
      return refuse(world, userId, OpError.TELEPORT_NOT_READY)
    }

    statusEffects.applyEffect(world, userId, StatusEffectId.TELEPORT_LOCK.id, level = 1, sourceEntityId = userId)

    // The body stands still while it vanishes.
    world.remove(userId, Path::class)
    world.remove(userId, Journey::class)
    world.add(userId, Teleporting(destination(world, userId), secondsUntilMove = vanishMs / 1000f))
    world.add(userId, EntityFx(vanish, vanishMs))

    return true
  }

  /** A wing in the middle of something else would cut it off unseen, or carry a trade or a logout away. */
  private fun isBusy(world: World, userId: EntityId): Boolean {
    return world.has(userId, Teleporting::class) ||
      world.has(userId, Casting::class) ||
      world.has(userId, Trading::class) ||
      world.has(userId, LogoutIntent::class)
  }

  private fun refuse(world: World, userId: EntityId, code: OpError): Boolean {
    world.get(userId, Account::class)?.let { out.sendToPlayer(it.accountId, OperationErrorSMSG(code)) }

    return false
  }
}
