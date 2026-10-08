package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.chat.ChatCommand
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.prop.SettlementFateService
import net.bestia.zone.world.stream.ChunkStreamConfig
import org.springframework.stereotype.Component

/**
 * `/raze <settlement>` - destroys every building of a settlement, which then falls. The development trigger for
 * a town's fall, gated by the same `chunk-stream.allow-debug-edits` as `/carve`. `/sites` names the index.
 */
@Component
class RazeChatCommand(
  private val fates: SettlementFateService,
  private val world: WorldView,
  private val settings: ChunkStreamConfig,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.TERRAIN

  override fun getHelpText(): String {
    return "/raze <SETTLEMENT> - Destroys every building of a settlement by its index, so it falls."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    if (!settings.allowDebugEdits) {
      LOG.info { "Refused /raze from $playerId: chunk-stream.allow-debug-edits is off" }
      return false
    }

    val settlement = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return false

    // On the tick: the site index, the residency and the divergence registry all belong to it.
    world.post {
      val razed = fates.raze(this, settlement)
      LOG.info { "Player $playerId razed $razed buildings of settlement $settlement" }
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    private val CMD_REGEX = Regex("""^/raze\s+(\d+)$""")
  }
}
