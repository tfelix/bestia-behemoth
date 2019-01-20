package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.Account.AccountType
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.factory.EntityFactory
import net.bestia.zoneserver.entity.factory.MobBlueprint

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
    private val entityFactory: EntityFactory
) : SubCommandModule(messageApi) {

  public override val helpText: String
    get() = "Usage: /spawn mob <MOB_DB_NAME> <POS_X> <POS_Y>"

  override fun isCommand(text: String): Boolean {
    return text.startsWith("mob ")
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.SUPER_GM
  }

  override val matcherPattern: Pattern = CMD_PATTERN

  override fun executeCheckedCommand(account: Account, text: String, matcher: Matcher) {
    LOG.info { "Command: /spawn mob triggered by account ${account.id}" }

    val mobName = matcher.group(1)
    val x = java.lang.Long.parseLong(matcher.group(2))
    val y = java.lang.Long.parseLong(matcher.group(3))

    val mobBlueprint = MobBlueprint(mobName, Point(x, y))
    entityFactory.build(mobBlueprint)
  }

  companion object {
    private val CMD_PATTERN = Pattern.compile("mob (\\w+) (\\d+) (\\d+)")
  }
}
