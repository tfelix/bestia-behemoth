package net.bestia.zoneserver.chat

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountType
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.entity.factory.ItemFactory

import java.util.regex.Matcher
import java.util.regex.Pattern

private val LOG = KotlinLogging.logger { }

/**
 * Spawns an item at the given coordinates
 *
 * @author Thomas Felix
 */
internal class SpawnItemModule(
    messageApi: MessageApi,
    private val itemFactory: ItemFactory
) : SubCommandModule(messageApi) {

  public override val helpText: String
    get() = "Usage: /spawn item <ITEM_DB_NAME | ITEM_ID> <POS_X> <POS_Y>"

  override fun isCommand(text: String): Boolean {
    return text.matches(CMD_PATTERN.toRegex())
  }

  override fun requiredUserLevel(): AccountType {
    return AccountType.SUPER_GM
  }

  override val matcherPattern: Pattern = CMD_PATTERN

  override fun executeCheckedCommand(account: Account, text: String, matcher: Matcher) {
    val itemIdent = matcher.group(1)
    val x = java.lang.Long.parseLong(matcher.group(2))
    val y = java.lang.Long.parseLong(matcher.group(3))
    LOG.info { "Command: /spawn item $itemIdent $x $y triggered by account ${account.id}" }

    val item = itemFactory.build(itemIdent, Point(x, y))
    messageApi.send(EntityEnvelope(item.id, item))
  }

  companion object {
    private val CMD_PATTERN = Pattern.compile("item (\\w+) (\\d+) (\\d+)")
  }
}
