package net.bestia.zoneserver.chat

import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import org.springframework.stereotype.Component

/**
 * Sends the server version to the client.
 *
 * @author Thomas Felix
 */
@Component
internal class ServerVersionChatCommand(
    msgApi: MessageApi,
    private val config: ZoneserverNodeConfig
) : BaseChatCommand(msgApi) {

  override val helpText: String
    get() = "Usage: /server"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/server ") || text == "/server"
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.USER
  }

  override fun executeCommand(account: Account, text: String) {
    val replyText = "Bestia Behemoth Server: ${config.serverName} v.${config.serverVersion}"
    sendSystemMessage(account.id, replyText)
  }
}
