package net.bestia.zoneserver.chat

import net.bestia.model.account.Account
import net.bestia.zoneserver.actor.MessageApi

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The sub modules are used to implement chat command modules which are used by
 * the [MetaChatCommand] to bundle together various chat commands.
 *
 * @author Thomas Felix
 */
internal abstract class SubCommandModule(messageApi: MessageApi) : BaseChatCommand(messageApi) {
  /**
   * Returns a matcher regexp pattern to check the command against.
   * @return
   */
  protected abstract val matcherPattern: Pattern

  abstract override val helpText: String

  protected abstract fun executeCheckedCommand(account: Account, text: String, matcher: Matcher)

  override fun executeCommand(account: Account, text: String) {
    if (account.userLevel < requiredUserLevel()) {
      return
    }

    val matcher = matcherPattern.matcher(text)
    if (!matcher.matches()) {
      sendSystemMessage(account.id, helpText)
      return
    }

    executeCheckedCommand(account, text, matcher)
  }
}
