package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.model.domain.Account
import net.bestia.model.domain.Account.Companion.UserLevel
import net.bestia.zoneserver.MessageApi

import java.util.regex.Matcher
import java.util.regex.Pattern

private val LOG = KotlinLogging.logger { }

/**
 * Spawns a mob for a given mob database name.
 *
 * @author Thomas Felix
 */
internal class MobSpawnModule(
    messageApi: MessageApi,
    private val mobFactory: MobFactory
) : SubCommandModule(messageApi) {

  public override val helpText: String
    get() = "Usage: /spawn mob <MOB_DB_NAME> <POS_X> <POS_Y>"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("mob ")
  }

  override fun requiredUserLevel(): UserLevel {
    return UserLevel.SUPER_GM
  }

  override val matcherPattern: Pattern = CMD_PATTERN

  override fun executeCheckedCommand(account: Account, text: String, matcher: Matcher) {
    LOG.info { "Command: /spawn mob triggered by account ${account.id}" }

    val mobName = matcher.group(1)
    val x = java.lang.Long.parseLong(matcher.group(2))
    val y = java.lang.Long.parseLong(matcher.group(3))

    mobFactory.build(mobName, x, y) ?: run {
      sendSystemMessage(account.id, String.format("Mob %s could not be spawned.", mobName))
    }
  }

  companion object {
    private val CMD_PATTERN = Pattern.compile("mob (\\w+) (\\d+) (\\d+)")
  }
}
