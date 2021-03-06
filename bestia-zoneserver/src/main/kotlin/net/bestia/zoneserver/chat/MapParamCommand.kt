package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.messages.chat.ChatResponse
import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.map.MapParameterRepository
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.zoneserver.actor.routing.MessageApi
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Returns the max current mapsize.
 *
 * /mapinfo
 *
 * @author Thomas Felix
 */
@Component
internal class MapParamCommand(
    messageApi: MessageApi,
    private val mapParamDao: MapParameterRepository
) : BaseChatCommand(messageApi) {

  public override val helpText: String
    get() = "Usage: /mapinfo"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/mapinfo")
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.USER
  }

  override fun executeCommand(account: Account, text: String) {
    LOG.debug("Chatcommand: /mapinfo triggered by account {}.", account.id)

    val mapParam = mapParamDao.findFirstByOrderByIdDesc()

    when (mapParam) {
      null -> {
        LOG.warn("No map parameter found inside database.")
        val msg = ChatResponse.getSystemMessage(account.id, "No map info found in database.")
        val envelope = ClientEnvelope(account.id, msg)
        messageApi.send(envelope)
      }
      else -> {
        val msg = ChatResponse.getSystemMessage(account.id, mapParam.toDetailString())
        val envelope = ClientEnvelope(account.id, msg)
        messageApi.send(envelope)
      }
    }
  }
}
