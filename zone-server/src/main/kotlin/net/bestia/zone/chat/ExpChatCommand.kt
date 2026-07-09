package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.status.Exp
import org.springframework.stereotype.Component

/**
 * Grants EXP to the command user's currently active bestia.
 */
@Component
class ExpChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val world: World
) : ChatCommand() {

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/exp\s+(\d+)$""")
  }

  override fun getHelpText(): String {
    return "/exp <AMOUNT> - Adds the given EXP to the command user's active bestia."
  }

  override val requiredAuthority: Authority = Authority.EXP

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false
    val amount = match.groupValues[1].toInt()

    val activeEntityId = connectionInfoService.getActiveEntityId(playerId)

    val exp = world.get(activeEntityId, Exp::class) ?: world.add(activeEntityId, Exp())
    exp.value += amount
    world.markChanged(activeEntityId, Exp::class)

    LOG.info { "Added $amount EXP to active entity $activeEntityId (player $playerId)" }

    return true
  }
}
