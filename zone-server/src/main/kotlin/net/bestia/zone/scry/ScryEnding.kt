package net.bestia.zone.scry

import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.ScrySight
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.RemoteViewpoints
import org.springframework.stereotype.Component

/**
 * Ends a Scry from whichever half goes first: the trance on the body, or the link on the far eye. Either one takes
 * the other half with it, so a hit on the body and a Dispel on the eye end the same sight.
 */
@Component
class ScryEnding(
  private val viewpoints: RemoteViewpoints,
  private val messages: OutMessageProcessor,
) {

  fun fromTrance(context: StatusEffectHookContext) {
    end(context, context.hostId)
  }

  fun fromEye(context: StatusEffectHookContext) {
    val world = context.world
    val eyeId = context.hostId
    val casterId = world.get(eyeId, ScryEye::class)?.casterId

    // The caster may have left the world, taking its sight along without a word. The eye is all that is left.
    if (casterId == null || world.get(casterId, ScrySight::class)?.eyeId != eyeId) {
      if (world.isAlive(eyeId)) world.destroy(eyeId)
      return
    }

    end(context, casterId)
  }

  private fun end(context: StatusEffectHookContext, casterId: EntityId) {
    val world = context.world
    val sight = world.get(casterId, ScrySight::class) ?: return
    if (sight.isEnded) return
    sight.isEnded = true

    world.remove(casterId, ScrySight::class)
    context.removeEffectFrom(casterId, StatusEffectId.SCRYING)
    context.removeEffectFrom(sight.eyeId, StatusEffectId.SCRY_EYE)
    if (world.isAlive(sight.eyeId)) world.destroy(sight.eyeId)

    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    viewpoints.close(accountId)
    messages.sendToPlayer(accountId, ScrySightSMSG.ENDED)
  }
}
