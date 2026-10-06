package net.bestia.zone.battle

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.attack.AttackExecutionService
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.ecs.attack.AttackTarget
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.world.prop.PropPromotionService
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.update

/**
 * Handles a player committing to a target entity, for whichever entity (master or an owned bestia) is
 * currently active. One click is a standing order rather than a single swing:
 * [net.bestia.zone.battle.ecs.attack.AttackSystem] keeps swinging at the target until one of them dies, or
 * the player moves, or picks something else.
 *
 * Nothing but a basic attack arrives here - no catalogue row, no script, no mana, no cast bar - so the
 * handler has nothing to validate beyond who is swinging: range, line of sight, attack delay, whether the
 * swing lands and what it takes off are all [AttackExecutionService]'s, the same path a mob's bite takes
 * through the `BasicAttack` behaviour-tree leaf. Casting a skill is [net.bestia.zone.casting.ActivateSkillHandler].
 */
@Component
class AttackEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val attackExecutionService: AttackExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val propPromotion: PropPromotionService,
) : TickMessageHandler<AttackEntityCMSG> {
  override val wire = decoder(MessageCase.ATTACK_ENTITY) { accountId, envelope ->
    AttackEntityCMSG.fromBnet(accountId, envelope.attackEntity)
  }

  override fun handle(world: World, msg: AttackEntityCMSG): Boolean {
    val attackerId = connectionInfoService.getActiveEntityId(msg.playerId)

    // AttackExecutionService refuses a dead attacker anyway; caught here too so a corpse does not
    // cancel its own pending logout on the way to being refused.
    if (deadActionGuard.refuses(world, attackerId, "attack")) {
      return true
    }

    // Swinging at something is player activity - abort any pending logout.
    logoutCancelService.cancelLogout(world, attackerId)

    // A tick handler runs between two ticks, so the damage AttackExecutionService stages applies at once
    // rather than being deferred. Does nothing when the attacker is no longer alive.
    world.modify(attackerId) { id ->
      // Here rather than only in BattleContextFactory: from a handler the adds apply immediately and the
      // swing below reads them straight back, whereas AttackSystem would see nothing yet and fizzle the
      // first hit on a pristine prop. See PropPromotionService's own KDoc.
      get(id, Position::class)?.let { attacker ->
        val reach = PropPromotionService.TARGETING_REACH
        propPromotion.promoteIfNeeded(this, msg.targetEntityId, attacker.toVec3L(), reach)
      }

      update(id, { AttackTarget(msg.targetEntityId) }) { it.targetEntityId = msg.targetEntityId }

      // Swung here as well as left to AttackSystem so the hit the player is watching lands on this message
      // rather than a tick later. The attack delay makes the two agree: whichever swings first arms it, and
      // clicking faster changes nothing.
      // TODO Take the weapon and its element off the attacker once an equipment system exists; until then
      //  everyone swings the bare-handed attack, the same one BasicAttack gives a mob.
      attackExecutionService.attack(this, id, msg.targetEntityId, BattleAttack.getBasicMeleeAttack())
    }

    return true
  }
}
