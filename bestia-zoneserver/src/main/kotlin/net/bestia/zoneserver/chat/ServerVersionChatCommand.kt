package net.bestia.zoneserver.chat

import net.bestia.model.domain.Account.Companion.UserLevel
import net.bestia.model.domain.Account
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.configuration.ZoneserverConfig
import org.springframework.stereotype.Component

/**
 * Sends the server version to the client.
 *
 * @author Thomas Felix
 */
@Component
internal class ServerVersionChatCommand(
    msgApi: MessageApi,
    private val config: ZoneserverConfig
) : BaseChatCommand(msgApi) {

  override val helpText: String
    get() = "Usage: /server"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/net/bestia/server ") || text == "/net/bestia/server"
  }

  override fun requiredUserLevel(): UserLevel {
    return UserLevel.USER
  }

  override fun executeCommand(account: Account, text: String) {
    val replyText = String.format("Bestia Behemoth Server (%s)", config.serverVersion)
    sendSystemMessage(account.id, replyText)
  }
}
