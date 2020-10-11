package net.bestia.zoneserver.chat

import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.zoneserver.actor.routing.MessageApi
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sends the server version to the client.
 *
 * @author Thomas Felix
 */
@Component
internal class ServerVersionChatCommand(
    msgApi: MessageApi,
    private val buildProperties: BuildProperties
) : BaseChatCommand(msgApi) {

  override val helpText: String
    get() = "Usage: /serverinfo"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("/serverinfo ") || text == "/serverinfo"
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.USER
  }

  override fun executeCommand(account: Account, text: String) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date.from(buildProperties.time))
    val replyText = "Bestia Behemoth Server: v.${buildProperties.version} ($dateStr)"
    sendSystemMessage(account.id, replyText)
  }
}
