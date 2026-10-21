package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.chat.ChatText
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.party.PartyAudience
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import org.springframework.stereotype.Service

/**
 * A line of party or guild chat written in the companion app. It reaches the group exactly as one typed in the game
 * does, and runs in the account's inbox so it is ordered with whatever that account's game client says meanwhile.
 * Local say has no app counterpart: the master is not there to be heard.
 */
@Service
class ChatCommand(
  private val owned: OwnedMasters,
  private val skillGate: BasicSkillGate,
  private val partyAudience: PartyAudience,
  private val guildAudience: GuildAudience,
  private val groupChat: GroupChat,
  private val inbox: AccountTaskExecutor,
  private val sessions: ConnectionInfoService,
  private val skillTree: MasterSkillTreeRegistry,
  private val learnedSkills: LearnedSkillRepository,
) {

  fun send(correlationId: String, accountId: Long, query: ZoneQuery.SendChat): BusReply {
    val type = when (query.channel) {
      "PARTY" -> ChatType.PARTY
      "GUILD" -> ChatType.GUILD
      else -> return BusReply.of(correlationId, Status.INVALID, "Unknown channel '${query.channel}'")
    }

    val text = ChatText.withoutControlCharacters(query.text)
    if (text.isBlank() || ChatText.isTooLong(text)) {
      return BusReply.of(correlationId, Status.INVALID, "A line is 1 to ${ChatText.MAX_TEXT_LENGTH} characters")
    }

    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        sendOwned(correlationId, accountId, query.masterId, type, text)
      } catch (e: Exception) {
        LOG.error(e) { "Chat line of master ${query.masterId} of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private fun sendOwned(correlationId: String, accountId: Long, masterId: Long, type: ChatType, text: String): BusReply {
    val master = owned.find(accountId, masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    if (basicSkillRank(accountId, masterId) < BasicSkillGate.CHAT_RANK) {
      return BusReply.ok(correlationId, ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.CHAT_LOCKED))
    }

    val members = when (type) {
      ChatType.PARTY -> partyAudience.ofMaster(masterId)
        ?: return BusReply.ok(correlationId, ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_PARTY))
      else -> guildAudience.ofMaster(masterId)
        ?: return BusReply.ok(correlationId, ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_GUILD))
    }

    groupChat.deliver(type, master.name, text, members)

    return BusReply.ok(correlationId, ZoneAnswer.ChatSent(null))
  }

  /**
   * [masterId]'s Basic Skill rank. [BasicSkillGate] answers off the live master, which only exists while it is
   * played, and then it is the one to ask: a rank bought this session is not stored yet. Otherwise the master's
   * stored rank, as of its last save.
   */
  private fun basicSkillRank(accountId: Long, masterId: Long): Int {
    if (sessions.isPlaying(accountId, masterId)) {
      return skillGate.rankOf(accountId)
    }

    // No Basic Skill in the tree: the gate's own answer, which fails open.
    val skillId = skillTree.basicSkillId ?: return skillGate.rankOf(accountId)

    return learnedSkills.findByMasterIdAndSkillId(masterId, skillId)?.level ?: 0
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
