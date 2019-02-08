package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.entity.factory.MobFactory

import java.util.regex.Matcher
import java.util.regex.Pattern

private val LOG = KotlinLogging.logger { }

/**
 * Spawns a mob for a given mob database name.
 *
 * @author Thomas Felix
 */
internal class SpawnMobModule(
    messageApi: MessageApi,
    private val mobFactory: MobFactory
) : SubCommandModule(messageApi) {

  public override val helpText: String
    get() = "Usage: /spawn mob <MOB_DB_NAME> <POS_X> <POS_Y>"

  override fun isCommand(text: String): Boolean {
    return text.matches(CMD_PATTERN.toRegex())
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.SUPER_GM
  }

  override val matcherPattern: Pattern = CMD_PATTERN

  override fun executeCheckedCommand(account: Account, text: String, matcher: Matcher) {
    val mobName = matcher.group(1)
    val x = java.lang.Long.parseLong(matcher.group(2))
    val y = java.lang.Long.parseLong(matcher.group(3))
    LOG.info { "Command: /spawn mob $mobName $x $y triggered by account ${account.id}" }

    val entity = mobFactory.build(mobName, Point(x, y))
    messageApi.send(EntityEnvelope(entity.id, entity))
  }

  companion object {
    private val CMD_PATTERN = Pattern.compile("mob (\\w+) (\\d+) (\\d+)")
  }
}
