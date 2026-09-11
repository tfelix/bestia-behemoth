package net.bestia.zone.chat

import net.bestia.account.Authority
import net.bestia.worldgen.pop.Households
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.ai.knowledge.KnowledgeService
import net.bestia.zone.ai.knowledge.TownKnowledge
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.townsfolk.HouseholdPlacement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component

/**
 * Reports what the town you are standing in remembers, and who in it remembers each thing.
 *
 * The knowledge model decides something a player can only ever see one conversation at a time - that the
 * innkeeper is not a shortcut past the village, and that one household somewhere holds a memory nobody
 * else does. `/sites` exists for the same reason one level down, and its KDoc makes the argument: a join
 * the generator cannot show you needs somewhere to be looked at.
 *
 * GM scaffolding, and the only reader of the model until an NPC can be spoken to.
 */
@Component
class KnowsChatCommand(
  private val connectionInfoService: ConnectionInfoService,
  private val knowledge: KnowledgeService,
  private val sites: SettlementSiteIndex,
  private val placement: HouseholdPlacement,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : ChatCommand() {

  override val requiredAuthority: Authority = Authority.SPAWN

  override fun getHelpText(): String {
    return "/knows [HOUSEHOLD] - What this town remembers, or what one household of it does."
  }

  override fun isMatch(cmdText: String): Boolean {
    return CMD_REGEX.matches(cmdText.trim())
  }

  override fun execute(playerId: Long, cmdText: String): Boolean {
    val entityId = connectionInfoService.getActiveEntityId(playerId)
    val position = world.read { get(entityId, Position::class) }?.toVec3L() ?: return false

    val site = sites.siteCovering(position.x, position.y)
    if (site == null) {
      reply(playerId, "You are not standing in any settlement.")
      return true
    }

    val town = knowledge.of(site.index)
    val household = CMD_REGEX.find(cmdText.trim())?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.toInt()

    if (household == null) {
      describeTown(site, town).forEach { reply(playerId, it) }
    } else {
      describeHousehold(site, town, household).forEach { reply(playerId, it) }
    }

    return true
  }

  /**
   * The distribution, which is what says whether the model is behaving.
   *
   * A town whose memories are all universal, or all sole-held, is a town where asking the right person
   * has stopped mattering - and that is visible here long before it is visible in a conversation.
   */
  private fun describeTown(site: SettlementSite, town: TownKnowledge): List<String> {
    val counts = town.holderCounts()
    val universal = town.universal()
    val households = site.population?.householdCount ?: 0

    if (universal.isEmpty() && counts.isEmpty()) {
      return listOf("Settlement ${site.index} remembers nothing at all.")
    }

    val sole = town.soleHeld()
    val lines = mutableListOf(
      "Settlement ${site.index}, $households households: ${town.all().size} memories " +
        "(${universal.size} known to everybody, ${counts.size} shared out, ${sole.size} held by one person)"
    )

    universal.sortedByDescending { it.importance }.take(SAMPLE).forEach {
      lines.add("  everybody: ${describe(it)}")
    }

    sole.sortedByDescending { it.second.importance }.take(SAMPLE).forEach { (household, memory) ->
      lines.add("  only household $household: ${describe(memory)}")
    }

    return lines
  }

  private fun describeHousehold(site: SettlementSite, town: TownKnowledge, household: Int): List<String> {
    val summary = site.population ?: return listOf("Settlement ${site.index} has no people.")
    if (household !in 0 until summary.householdCount) {
      return listOf("Settlement ${site.index} has ${summary.householdCount} households, numbered from zero.")
    }

    val expanded = Households.one(summary, household)
    val occupation = placement.occupationFor(expanded, expanded.head)
    val held = town.heldBy(household)
    val oldest = expanded.members.maxOfOrNull { it.age } ?: 0

    val lines = mutableListOf(
      "Household $household of settlement ${site.index}: ${occupation.label}, " +
        "${expanded.members.size} people, oldest $oldest. Knows ${held.size} things:"
    )
    held.sortedByDescending { it.importance }.forEach { lines.add("  ${describe(it)}") }

    return lines
  }

  /**
   * The key and its slots rather than a sentence.
   *
   * There is no sentence to print: a memory is a translation key and its arguments, and the English for
   * it lives in the client's translation file. Reading the key here is also the better check - it is what
   * a phrasing will have to be written against.
   */
  private fun describe(memory: Knowledge): String {
    val slots = memory.slots.entries.joinToString(", ") { (name, slot) -> "$name=${value(slot)}" }

    return "${memory.key} {$slots} imp=${memory.importance} ${memory.locality}"
  }

  private fun value(slot: Knowledge.Slot): String {
    return when (slot) {
      is Knowledge.Slot.Name -> slot.value
      is Knowledge.Slot.Token -> "<${slot.key}>"
      is Knowledge.Slot.Number -> slot.value.toString()
    }
  }

  private fun reply(playerId: Long, text: String) {
    out.sendToPlayer(playerId, ChatSMSG(text = text, type = ChatCMSG.Type.COMMAND))
  }

  private companion object {
    private val CMD_REGEX = Regex("""^/knows(?:\s+(\d+))?$""")

    /** Enough to see the shape of it without filling the chat window. */
    private const val SAMPLE = 5
  }
}
