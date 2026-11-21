package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.chat.ChatText
import net.bestia.zone.chat.ChatType
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.chat.WhisperChat
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.party.PartyAudience
import org.springframework.stereotype.Service

/**
 * Party and guild chat and whispers written in the companion app. A line reaches its readers exactly as one typed in
 * the game does, and runs in the account's inbox so it is ordered with whatever that account's game client says
 * meanwhile. Local say has no app counterpart: the master is not there to be heard.
 */
@Service
class ChatCommand(
  private val owned: OwnedMasters,
  private val basicSkill: MasterBasicSkill,
  private val partyAudience: PartyAudience,
  private val guildAudience: GuildAudience,
  private val groupChat: GroupChat,
  private val whisperChat: WhisperChat,
  private val inbox: AccountTaskExecutor,
) {

  fun send(correlationId: String, accountId: Long, query: ZoneQuery.SendChat): BusReply {
    val type = when (query.channel) {
      "PARTY" -> ChatType.PARTY
      "GUILD" -> ChatType.GUILD
      else -> return BusReply.of(correlationId, Status.INVALID, "Unknown channel '${query.channel}'")
    }

    return speak(correlationId, accountId, query.masterId, query.text) { master, text ->
      val members = when (type) {
        ChatType.PARTY -> partyAudience.ofMaster(query.masterId) ?: return@speak ZoneAnswer.ChatSent.NOT_IN_PARTY
        else -> guildAudience.ofMaster(query.masterId) ?: return@speak ZoneAnswer.ChatSent.NOT_IN_GUILD
      }
      groupChat.deliver(type, master.name, text, members)
      null
    }
  }

  fun whisper(correlationId: String, accountId: Long, query: ZoneQuery.SendWhisper): BusReply {
    val targetName = query.targetName.trim()
    if (targetName.isEmpty()) {
      return BusReply.of(correlationId, Status.INVALID, "A whisper names a master")
    }

    return speak(correlationId, accountId, query.masterId, query.text) { master, text ->
      ZoneAnswer.ChatSent.TARGET_UNAVAILABLE.takeUnless { whisperChat.deliver(master, targetName, text) }
    }
  }

  /**
   * Checks what every line from the app shares, then lets [deliver] say it as [masterId]. [deliver] answers with
   * the refusal, or null once the line went out.
   */
  private fun speak(
    correlationId: String,
    accountId: Long,
    masterId: Long,
    rawText: String,
    deliver: (Master, String) -> String?,
  ): BusReply {
    val text = ChatText.withoutControlCharacters(rawText)
    if (text.isBlank() || ChatText.isTooLong(text)) {
      return BusReply.of(correlationId, Status.INVALID, "A line is 1 to ${ChatText.MAX_TEXT_LENGTH} characters")
    }

    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        speakOwned(correlationId, accountId, masterId) { deliver(it, text) }
      } catch (e: Exception) {
        LOG.error(e) { "Chat line of master $masterId of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private fun speakOwned(correlationId: String, accountId: Long, masterId: Long, deliver: (Master) -> String?): BusReply {
    val master = owned.find(accountId, masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    if (!basicSkill.mayChat(accountId, masterId)) {
      return BusReply.ok(correlationId, ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.CHAT_LOCKED))
    }

    return BusReply.ok(correlationId, ZoneAnswer.ChatSent(deliver(master)))
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
