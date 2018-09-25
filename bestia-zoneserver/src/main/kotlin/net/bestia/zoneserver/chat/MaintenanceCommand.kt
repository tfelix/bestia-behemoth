package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.domain.Account
import net.bestia.model.domain.Account.Companion.UserLevel
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.client.LogoutService
import net.bestia.zoneserver.configuration.RuntimeConfigService
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Allows admins to set the server into maintenance mode. Use with caution since
 * this will disconnect ALL users! It can only switch the server into partial
 * maintenance since full maintainance would also disconnect the admin itself.
 *
 * @author Thomas Felix
 */
@Component
internal class MaintenanceCommand(
    messageApi: MessageApi,
    private val logoutService: LogoutService,
    private val config: RuntimeConfigService
) : BaseChatCommand(messageApi) {

  override val helpText: String
    get() = "Usage: /maintenance [TRUE|FALSE]"

  override fun isCommand(text: String): Boolean {
    return text.matches(CMD_START_REGEX)
  }

  override fun requiredUserLevel(): UserLevel {
    return UserLevel.ADMIN
  }

  override fun executeCommand(account: Account, text: String) {
    val match = CMD_PATTERN.find(text)

    if (match == null) {
      printError(account.id)
      return
    }

    val isMaintenance = match.groups[0]?.value?.toBoolean() ?: false
    LOG.info("Account {} set maintenance to: {}", account.id, isMaintenance)

    if (isMaintenance) {
      sendSystemMessage(account.id, "Server maintenance: true")
      config.maintenanceMode = MaintenanceLevel.PARTIAL
      logoutService.logoutAllUsersBelow(UserLevel.SUPER_GM)
    } else {
      sendSystemMessage(account.id, "Server maintenance: false")
      config.maintenanceMode = MaintenanceLevel.NONE
    }
  }

  private fun printError(accId: Long) {
    sendSystemMessage(accId, "Usage: /maintenance [true, false]")
  }

  companion object {
    private val CMD_START_REGEX = "^/maintenance .*".toRegex()
    private val CMD_PATTERN = "/maintenance (true|false)".toRegex(RegexOption.IGNORE_CASE)
  }
}
