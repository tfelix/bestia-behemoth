package net.bestia.zone.chat

import net.bestia.account.Authority
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * Reports the settlement the caller is standing in: its size, its buildings, and who works where.
 *
 * The join this prints - a trade to the building that houses it, and a building to a doorstep you can walk
 * to - exists nowhere the generator can show you, so until townsfolk stand in these doorways this is the
 * only way to see whether it is right. Replies in chat rather than to the log, unlike `/ambient`, because
 * the answer is about the ground under the caller's feet and is worth reading where they are standing.
 */
@Component
class SitesChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val siteIndex: SettlementSiteIndex,
  private val worldService: WorldService,
  private val world: WorldView,
  private val out: OutMessageProcessor
) : ChatCommand() {

  override fun getHelpText(): String {
    return "/sites - Describes the settlement you are standing in: buildings, trades and their doors."
  }

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun isMatch(cmdText: String): Boolean {
    return cmdText.trim() == "/sites"
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val entityId = connectionInfoService.getActiveEntityId(playerId)
    val position = world.read { get(entityId, Position::class) }?.toVec3L() ?: return false

    val site = siteIndex.siteCovering(position.x, position.y)
    if (site == null) {
      reply(playerId, "You are not standing in any settlement.")
      return true
    }

    reply(playerId, headline(site))
    for (line in describeBuildings(site)) {
      reply(playerId, line)
    }
    reply(playerId, nearestDoor(site, position.x, position.y))

    return true
  }

  private fun headline(site: SettlementSite): String {
    val people = site.population
    val size = if (people == null) {
      "no economy marker"
    } else {
      "${people.population} people, ${people.householdCount} households"
    }

    return "Settlement ${site.index}: ${site.tier.label}, $size, ${site.buildings.size} buildings."
  }

  private fun describeBuildings(site: SettlementSite): List<String> {
    return site.buildings
      .groupBy { it.function }
      .entries
      .sortedBy { it.key.ordinal }
      .map { (function, buildings) ->
        val trades = buildings
          .filter { it.businessType != SettlementSiteIndex.NO_BUSINESS }
          .joinToString(", ") { BusinessCatalogue.ALL[it.businessType].label }

        if (trades.isEmpty()) {
          "  ${function.label}: ${buildings.size}"
        } else {
          "  ${function.label}: ${buildings.size} ($trades)"
        }
      }
  }

  private fun nearestDoor(site: SettlementSite, x: Long, y: Long): String {
    val metres = worldService.config.voxelSize
    val nearest = site.buildings.minByOrNull { building ->
      val dx = building.door.x - x * metres
      val dy = building.door.y - y * metres
      dx * dx + dy * dy
    } ?: return "  no buildings to stand at."

    val doorX = Math.round(nearest.door.x / metres)
    val doorY = Math.round(nearest.door.y / metres)

    return "  nearest door: ${nearest.function.label} at $doorX/$doorY."
  }

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }
}
