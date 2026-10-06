package net.bestia.zone.chat.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.bestia.persistence.BestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Component
import net.bestia.zone.chat.ChatCommand

/**
 * Spawns an NPC bestia via [BestiaEntitySpawner], the same spawner the [net.bestia.zone.spawn.ecs.SpawnerSystem]
 * uses - so the mob gets its AI brain and known skills like a naturally spawned one.
 */
@Component
class SpawnChatCommand(
  private val bestiaRepository: BestiaRepository,
  private val bestiaEntitySpawner: BestiaEntitySpawner,
  private val world: WorldView
) : ChatCommand() {

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/spawn\s+(\S+)\s+(-?\d+)\s+(-?\d+)$""")
  }

  override fun getHelpText(): String {
    return "/spawn <ENTITY_IDENTIFIER> <X> <Y> - Spawns an NPC bestia at the given position."
  }

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val identifier = match.groupValues[1]
    val x = match.groupValues[2].toLong()
    val y = match.groupValues[3].toLong()

    val bestia = bestiaRepository.findByIdentifier(identifier)

    if (bestia == null) {
      LOG.warn { "Spawn command failed: bestia '$identifier' not found" }
      return false
    }

    // TODO add height check if we get more complex maps
    val entityId = world.read { bestiaEntitySpawner.spawnMob(this, bestiaId = bestia.id, pos = Vec3L(x, y, 0L)) }

    LOG.info { "Spawned bestia ${bestia.identifier} as entity $entityId at $x/$y (player $playerId)" }

    return true
  }
}
