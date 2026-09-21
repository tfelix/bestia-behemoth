package net.bestia.zone.battle

import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.ecs.battle.attack.AttackTarget
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.world.prop.PropPromotionService
import org.springframework.stereotype.Component

/**
 * Handles a player committing to a target entity, for whichever entity (master or an owned bestia) is
 * currently active. One click is a standing order rather than a single swing:
 * [net.bestia.zone.ecs.battle.attack.AttackSystem] keeps swinging at the target until one of them dies, or
 * the player moves, or picks something else.
 *
 * Nothing but a basic attack arrives here - no catalogue row, no script, no mana, no cast bar - so the
 * handler has nothing to validate beyond who is swinging: range, line of sight, attack delay, whether the
 * swing lands and what it takes off are all [AttackExecutionService]'s, the same path a mob's bite takes
 * through the `BasicAttack` behaviour-tree leaf. Casting a skill is [ActivateSkillHandler].
 */
@Component
class AttackEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val attackExecutionService: AttackExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val propPromotion: PropPromotionService,
) : InMessageProcessor.IncomingMessageHandler<AttackEntityCMSG> {
  override val handles = AttackEntityCMSG::class

  override fun handle(msg: AttackEntityCMSG): Boolean {
    val attackerId = connectionInfoService.getActiveEntityId(msg.playerId)

    // AttackExecutionService refuses a dead attacker anyway; caught here too so a corpse does not
    // cancel its own pending logout on the way to being refused.
    if (deadActionGuard.refuses(attackerId, "attack")) {
      return true
    }

    // Swinging at something is player activity - abort any pending logout.
    logoutCancelService.cancelLogout(attackerId)

    // Inside the caster's own scope because AttackExecutionService resolves inline against the live World:
    // it stages the damage and broadcasts, which both need the lock held. A handler scope never runs nested
    // inside a tick, so the staging applies immediately rather than being deferred.
    // Returns null - and so does nothing - when the attacker is no longer alive.
    world.modify(attackerId) { id ->
      // Here rather than only in BattleContextFactory: from a handler the adds apply immediately and the
      // swing below reads them straight back, whereas AttackSystem would see nothing yet and fizzle the
      // first hit on a pristine prop. See PropPromotionService's own KDoc.
      propPromotion.promoteIfNeeded(this, msg.targetEntityId)

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
