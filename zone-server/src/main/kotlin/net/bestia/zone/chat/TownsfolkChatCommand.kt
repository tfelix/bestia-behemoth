package net.bestia.zone.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Authority
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * Puts one townsperson on the ground with a trade, for looking at a day.
 *
 * `/spawn townsfolk_commoner` already places somebody, so what this adds is the half a bestia catalogue
 * cannot carry: an occupation belongs to the individual, not to the species. The post is looked up from
 * the settlement standing where they are put, so a guard dropped in a village walks to its barracks.
 *
 * GM scaffolding. Who lives in a town and what they do there becomes a property of the settlement in its
 * own right shortly; until then this is how the shift is watched.
 */
@Component
class TownsfolkChatCommand(
  private val occupations: OccupationCatalogue,
  private val bestiaRepository: BestiaRepository,
  private val spawner: BestiaEntitySpawner,
  private val sites: SettlementSiteIndex,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun getHelpText(): String {
    return "/townsfolk <OCCUPATION> <X> <Y> - Puts a townsperson of that trade there. " +
      "Known: ${occupations.ids().sorted().joinToString(", ")}"
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val match = CMD_REGEX.find(cmdText.trim()) ?: return false

    val occupation = occupations.get(match.groupValues[1])
    if (occupation == null) {
      reply(playerId, "No such occupation. Known: ${occupations.ids().sorted().joinToString(", ")}")
      return true
    }

    val home = Vec3L(match.groupValues[2].toLong(), match.groupValues[3].toLong(), 0L)
    val bestia = bestiaRepository.findByIdentifier(COMMONER) ?: run {
      reply(playerId, "The '$COMMONER' mob is missing from the catalogue.")
      return true
    }

    val post = postFor(occupation, home)
    val memory = Blackboard().apply {
      set(TownsfolkDomain.OCCUPATION, occupation, Blackboard.PERMANENT)
      post?.let { set(TownsfolkDomain.WORK_POSITION, it, Blackboard.PERMANENT) }
    }

    val entityId = spawner.spawnMob(world, bestiaId = bestia.id, pos = home, aiMemory = memory)

    LOG.info { "Spawned ${occupation.id} as entity $entityId at $home, post $post (player $playerId)" }
    reply(playerId, describe(occupation, home, post))

    return true
  }

  /**
   * The nearest building in the covering settlement that keeps this trade.
   *
   * Null when there is no settlement here, when the occupation keeps no shop, or when this town has no
   * such trade - all three of which are ordinary, and all three leave somebody who loiters and sleeps
   * rather than one who freezes: `Goals.WORK_SHIFT` is unavailable without a post.
   */
  private fun postFor(occupation: Occupation, home: Vec3L): Vec3L? {
    val trade = occupation.businessType ?: return null
    val type = BusinessCatalogue.ALL.indexOfFirst { it.id == trade }
    if (type < 0) return null

    val site = sites.siteCovering(home.x, home.y) ?: return null

    return site.buildingsFor(type)
      .minByOrNull { building ->
        val dx = building.door.x - home.x
        val dy = building.door.y - home.y
        dx * dx + dy * dy
      }
      ?.let { Vec3L(it.door.x.toLong(), it.door.y.toLong(), home.z) }
  }

  private fun describe(occupation: Occupation, home: Vec3L, post: Vec3L?): String {
    val shift = occupation.shift?.toString() ?: "no shift"
    val where = post?.let { "post at ${it.x}/${it.y}" }
      ?: "no post here, so they will keep to the street"

    return "${occupation.label} at ${home.x}/${home.y}: works $shift, sleeps ${occupation.rest}, $where"
  }

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val CMD_REGEX = Regex("""^/townsfolk\s+(\S+)\s+(-?\d+)\s+(-?\d+)$""")

    /** The one townsfolk archetype there is; an occupation is what tells two of them apart. */
    private const val COMMONER = "townsfolk_commoner"
  }
}
