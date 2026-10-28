package net.bestia.zone.emote

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.skill.Casting
import net.bestia.zone.battle.ecs.skill.Crafting
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import org.springframework.stereotype.Service

/**
 * Plays an emote on the entity a player controls, for everyone who sees it. Both ways of asking end here: the
 * shortcut's [net.bestia.zone.emote.net.EmoteCMSG] and the typed `/cheer` of
 * [net.bestia.zone.chat.net.EmoteChatCommand].
 *
 * The emote is the entity's [Animation], the same channel the AI's sleep travels on, so an observer who walks up
 * mid-emote gets it in the entity's snapshot like any other pose. [net.bestia.zone.emote.ecs.EmoteSystem] ends it.
 */
@Service
class EmoteService(
  private val connectionInfoService: ConnectionInfoService,
  private val basicSkillGate: BasicSkillGate,
  private val deadActionGuard: DeadActionGuard,
  private val outMessageProcessor: OutMessageProcessor,
) {

  fun perform(world: World, accountId: Long, kind: Animation.AnimationKind) {
    if (!kind.isEmote) {
      // IDLE and SLEEP are poses only the server sets; an honest client offers neither.
      LOG.warn { "Player $accountId asked for $kind, which is not an emote, ignoring" }
      return
    }

    if (!basicSkillGate.mayEmote(accountId)) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.BASIC_SKILL_EMOTE_LOCKED))
      return
    }

    val entityId = connectionInfoService.getActiveEntityId(accountId)

    if (deadActionGuard.refuses(world, entityId, "emote")) {
      return
    }

    // Each of these drives the body itself, and EmoteSystem would stand the entity up again on the next tick.
    if (world.has(entityId, Path::class) || world.has(entityId, Casting::class) || world.has(entityId, Crafting::class)) {
      outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.EMOTE_NOT_NOW))
      return
    }

    val animation = world.get(entityId, Animation::class)
    if (animation == null) {
      LOG.warn { "Entity $entityId of player $accountId has no Animation, so it cannot emote" }
      return
    }

    // A once emote plays out before the next one starts. That is the spam guard, and it is also what replays a
    // second cheer: the pose goes back to IDLE in between, so asking again is a real change the client sees.
    if (animation.emoteSecondsLeft > 0f) {
      LOG.debug { "Entity $entityId is still playing ${animation.currentAnimation}, dropping $kind" }
      return
    }

    animation.startEmote(kind)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
