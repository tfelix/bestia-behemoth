package net.bestia.zoneserver.chat

import net.bestia.model.account.Account
import net.bestia.model.account.AccountType

interface ChatCommand {

  /**
   * Checks if this is a correct chat command and can be handled by this
   * command.
   *
   * @param text
   * Chat text.
   * @return TRUE if the command can be handled by this [ChatCommand]
   * implementation.
   */
  fun isCommand(text: String): Boolean

  /**
   * Executes the chat command which was issued by the given account id.
   *
   * @param account Account which issued this command.
   * @param text
   * Chat text typed by the user.
   */
  fun executeCommand(account: Account, text: String)

  /**
   * Gives the minimum user level required to execute this command.
   *
   * @return The minimum userlevel required to use this command.
   */
  fun requiredUserLevel(): AccountType
}
