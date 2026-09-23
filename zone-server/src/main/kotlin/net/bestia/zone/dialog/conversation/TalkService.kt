package net.bestia.zone.dialog.conversation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Talking to somebody: the checks, and the one place a [ConversationSMSG] is sent.
 *
 * Split from [ConversationService], which decides *what* is said. This decides whether it may be said
 * at all - the same split `DialogService` makes against the definition registry, and it is what lets
 * the node logic be tested without a session or a world.
 */
@Service
class TalkService(
  private val speakers: SpeakerResolver,
  private val conversation: ConversationService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) {

  fun open(accountId: AccountId, actor: EntityId, target: EntityId) {
    send(accountId, actor, target) { conversation.open(it) }
  }

  fun choose(accountId: AccountId, actor: EntityId, target: EntityId, topicId: Int) {
    send(accountId, actor, target) { conversation.nodeFor(Asker(accountId, actor), it, topicId) }
  }

  /**
   * Range is re-checked on **every** step, not only on the first.
   *
   * A conversation holds no state, so there is nothing to expire when a player walks off - and without
   * this they could open a conversation, walk across the town and keep asking questions. The entity id
   * on the wire is what makes it cheap: the server re-resolves both ends each time anyway.
   */
  private fun send(accountId: AccountId, actor: EntityId, target: EntityId, node: (Speaker) -> ConversationNode?) {
    val speaker = speakers.of(target)
    if (speaker == null) {
      // Not a townsperson. An honest client only offers this on somebody who can talk, so this is a
      // stale id or a hand-built message - neither is worth a code the player would have to read.
      LOG.debug { "Account $accountId tried to talk to $target, which is nobody" }
      return
    }

    if (!withinReach(actor, target)) {
      out.sendToPlayer(accountId, OperationErrorSMSG(OperationErrorProto.OpError.TALK_OUT_OF_RANGE))
      return
    }

    val answer = node(speaker)
    if (answer == null) {
      LOG.debug { "${speaker.identity} has nothing to say to topic asked by $accountId" }
      return
    }

    out.sendToPlayer(accountId, ConversationSMSG(target, speaker.name, answer))
  }

  private fun withinReach(one: EntityId, other: EntityId): Boolean {
    return world.read {
      val onePos = get(one, Position::class)?.toVec3L() ?: return@read false
      val otherPos = get(other, Position::class)?.toVec3L() ?: return@read false

      onePos.distance(otherPos) <= MAX_TALK_RANGE
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * `TradeService.MAX_TRADE_RANGE`, and for its reasons: `Vec3L.distance` is horizontal and truncates,
     * so this errs lenient and ignores height. Not shared as a constant because the two are allowed to
     * diverge - you can shout further than you can hand somebody a sword.
     */
    const val MAX_TALK_RANGE = 10L
  }
}
