package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a player swinging at a target entity, for whichever entity (master or an owned bestia) is
 * currently active.
 *
 * Nothing but a basic attack arrives here - no catalogue row, no script, no mana, no cast bar - so the
 * handler has nothing to validate beyond who is swinging: range, line of sight, whether the swing lands
 * and what it takes off are all [AttackExecutionService]'s, the same path a mob's bite takes through the
 * `BasicAttack` behaviour-tree leaf. Casting a skill is [ActivateSkillHandler].
 */
@Component
class AttackEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val attackExecutionService: AttackExecutionService,
  private val logoutCancelService: LogoutCancelService,
  private val deadActionGuard: DeadActionGuard,
) : InMessageProcessor.IncomingMessageHandler<AttackEntityCMSG> {
  override val handles = AttackEntityCMSG::class

  override fun handle(msg: AttackEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }

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
      // TODO Take the weapon and its element off the attacker once an equipment system exists; until then
      //  everyone swings the bare-handed attack, the same one BasicAttack gives a mob.
      attackExecutionService.attack(this, id, msg.targetEntityId, BattleAttack.getBasicMeleeAttack())
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
