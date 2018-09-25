package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.dao.MapParameterDAO
import net.bestia.model.domain.Account
import net.bestia.model.domain.Account.Companion.UserLevel
import net.bestia.zoneserver.MessageApi
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Returns the max current mapsize.
 *
 * /mapinfo
 *
 *
 * @author Thomas Felix
 */
@Component
internal class MapParamCommand(
    messageApi: MessageApi,
    private val mapParamDao: MapParameterDAO
) : BaseChatCommand(messageApi) {

  public override val helpText: String
    get() = "Usage: /mapinfo"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/mapinfo")
  }

  override fun requiredUserLevel(): UserLevel {
    return UserLevel.USER
  }

  override fun executeCommand(account: Account, text: String) {
    LOG.debug("Chatcommand: /mapinfo triggered by account {}.", account.id)

    val mapParam = mapParamDao.findFirstByOrderByIdDesc()

    when (mapParam) {
      null -> {
        LOG.warn("No map parameter found inside database.")
        val msg = ChatMessage.getSystemMessage(account.id, "No map info found in database.")
        val envelope = ClientEnvelope(account.id, msg)
        messageApi.send(envelope)
      }
      else -> {
        val msg = ChatMessage.getSystemMessage(account.id, mapParam.toDetailString())
        val envelope = ClientEnvelope(account.id, msg)
        messageApi.send(envelope)
      }
    }
  }
}
