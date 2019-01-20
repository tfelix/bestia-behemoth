package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType

import java.util.ArrayList

private val LOG = KotlinLogging.logger { }

/**
 * This command incorporates another level of commands. This means it can
 * contain two levels. Like for example:
 *
 * `
 * /entity SECOND_CMD PARAMS
` *
 *
 * @author Thomas Felix
 */
internal class MetaChatCommand(
    metaCommandStr: String
) : ChatCommand {
  private val modules = ArrayList<BaseChatCommand>()
  private val commandStr = metaCommandStr.trim { it <= ' ' } + " "

  /**
   * Adds a module to the execution of this meta chat command.
   *
   * @param module
   * A new module to add.
   */
  fun addCommandModule(module: BaseChatCommand) {
    modules.add(module)
  }

  override fun isCommand(text: String): Boolean {
    return text.startsWith(commandStr)
  }

  /**
   * The user can potentially execute the lowest command module included in
   * this [MetaChatCommand]. Thus the required level is the lowest level
   * found in all command modules. The modules will have to check for
   * themselves upon execution if this requirement is met.
   *
   * @return The minimum userlevel required to use this command.
   */
  override fun requiredUserLevel(): AccountType {
    var level = AccountType.ADMIN
    for (module in modules) {
      if (module.requiredUserLevel() < level) {
        level = module.requiredUserLevel()
      }
    }
    return level
  }

  override fun executeCommand(account: Account, text: String) {
    // Strip away our prefix of the command.
    val strippedText = text.substring(commandStr.length)

    // Look if we find a sub command with this prefix.
    for (module in modules) {
      if (module.isCommand(strippedText)) {
        try {
          module.executeCommand(account, strippedText)
        } catch (e: Exception) {
          LOG.warn("Error while executing chat command: {}", text, e)
          return
        }

      }
    }
  }
}
