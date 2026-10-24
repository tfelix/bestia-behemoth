package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.AnswerCache
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The account's masters, and for each its sheet, bestias, bags, party and guild. Every figure that can go stale carries
 * `asOf`, when it was last true, in epoch milliseconds.
 */
@RestController
@RequestMapping("/v1/companion/masters")
class MastersController(
  private val zone: ZoneClient,
  private val cache: AnswerCache,
) {

  data class MasterSummary(val id: Long, val slot: Int, val name: String, val level: Int, val home: String, val inWorld: Boolean)

  data class Position(val eastMetres: Double, val northMetres: Double)

  data class Stats(
    val strength: Int,
    val vitality: Int,
    val intelligence: Int,
    val dexterity: Int,
    val willpower: Int,
    val agility: Int,
  )

  data class MasterSheet(
    val id: Long,
    val name: String,
    val level: Int,
    val exp: Int,
    val skillPoints: Int,
    val statusPoints: Int,
    val stats: Stats,
    val currentHealth: Int?,
    val home: String,
    val position: Position,
    val inWorld: Boolean,
    val asOf: Long,
  )

  data class Bestia(
    val id: Long,
    val name: String?,
    val speciesId: Long,
    val species: String,
    val level: Int,
    val stance: String,
    val aggression: Int,
    val inWorld: Boolean,
    val position: Position,
    val asOf: Long,
    val metresPerSecond: Double,
    /** Where it is travelling, null when it stands. */
    val journey: Journey?,
  )

  /** [route] is the way still ahead as planned; [waypoints] from [nextIndex] on are still to visit. */
  data class Journey(
    val name: String?,
    val waypoints: List<Position>,
    val nextIndex: Int,
    val route: List<Position>,
  )

  data class Slot(
    val itemId: Long,
    val item: String,
    val amount: Int,
    val equippedIn: String?,
    val durability: Int,
    val maxDurability: Int,
    val upgradeLevel: Int,
  )

  /** [kind] is MASTER or BESTIA. */
  data class Holder(val kind: String, val id: Long, val name: String?, val slots: List<Slot>)

  data class PartyMember(val masterId: Long, val name: String, val level: Int, val online: Boolean)

  data class Party(val name: String, val ownerMasterId: Long, val members: List<PartyMember>)

  /** [party] is null when the master is in none. */
  data class PartyView(val party: Party?)

  /** [lastAreaName] and [lastSeen], epoch milliseconds, say where and when an offline member left the world. */
  data class GuildMember(
    val masterId: Long,
    val name: String,
    val level: Int,
    val rankTitle: String,
    val isLeader: Boolean,
    val online: Boolean,
    val lastAreaName: String,
    val lastSeen: Long,
  )

  /** [emblemHash] names the picture at `/v1/companion/emblems/{emblemHash}.png`; empty while it has none. */
  data class Guild(
    val name: String,
    val level: Int,
    val motdTitle: String,
    val motdBody: String,
    val emblemHash: String,
    val members: List<GuildMember>,
  )

  /** [guild] is null when the master is in none. */
  data class GuildView(val guild: Guild?)

  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long): List<MasterSummary> {
    val masters: ZoneAnswer.Masters = ask(accountId, ZoneQuery.ListMasters)

    return masters.masters.map { MasterSummary(it.id, it.slot, it.name, it.level, it.home, it.inWorld) }
  }

  @GetMapping("/{masterId}")
  fun sheet(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): MasterSheet {
    val s: ZoneAnswer.Sheet = ask(accountId, ZoneQuery.MasterSheet(masterId))

    return MasterSheet(
      id = s.id,
      name = s.name,
      level = s.level,
      exp = s.exp,
      skillPoints = s.skillPoints,
      statusPoints = s.statusPoints,
      stats = Stats(s.strength, s.vitality, s.intelligence, s.dexterity, s.willpower, s.agility),
      currentHealth = s.currentHealth,
      home = s.home,
      position = Position(s.eastMetres, s.northMetres),
      inWorld = s.inWorld,
      asOf = s.asOfEpochMs,
    )
  }

  @GetMapping("/{masterId}/bestias")
  fun bestias(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Bestia> {
    val roster: ZoneAnswer.Roster = ask(accountId, ZoneQuery.BestiaRoster(masterId))

    return roster.bestias.map {
      Bestia(
        id = it.id,
        name = it.name,
        speciesId = it.speciesId,
        species = it.species,
        level = it.level,
        stance = it.stance,
        aggression = it.aggression,
        inWorld = it.inWorld,
        position = Position(it.eastMetres, it.northMetres),
        asOf = it.asOfEpochMs,
        metresPerSecond = it.metresPerSecond,
        journey = it.journey?.let { journey ->
          Journey(
            name = journey.name,
            waypoints = journey.waypoints.map { point -> Position(point.east, point.north) },
            nextIndex = journey.nextIndex,
            route = journey.route.map { point -> Position(point.east, point.north) },
          )
        },
      )
    }
  }

  @GetMapping("/{masterId}/inventory")
  fun inventory(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Holder> {
    val bag: ZoneAnswer.Bag = ask(accountId, ZoneQuery.Inventory(masterId))

    return bag.holders.map { holder ->
      Holder(
        holder.kind,
        holder.id,
        holder.name,
        holder.slots.map { Slot(it.itemId, it.item, it.amount, it.equippedIn, it.durability, it.maxDurability, it.upgradeLevel) },
      )
    }
  }

  @GetMapping("/{masterId}/party")
  fun party(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): PartyView {
    val info: ZoneAnswer.PartyInfo = ask(accountId, ZoneQuery.PartyRoster(masterId))

    return PartyView(
      info.party?.let { party ->
        Party(party.name, party.ownerMasterId, party.members.map { PartyMember(it.masterId, it.name, it.level, it.online) })
      }
    )
  }

  /** Only species the master has seen; every other number in the catalogue is unseen. */
  data class CompendiumEntry(val speciesId: Long, val caught: Boolean, val firstSeen: Long)

  @GetMapping("/{masterId}/compendium")
  fun compendium(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<CompendiumEntry> {
    val data: ZoneAnswer.CompendiumData = ask(accountId, ZoneQuery.Compendium(masterId))

    return data.entries.map { CompendiumEntry(it.speciesId, it.caught, it.firstSeenEpochMs) }
  }

  @GetMapping("/{masterId}/guild")
  fun guild(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): GuildView {
    val info: ZoneAnswer.GuildInfo = ask(accountId, ZoneQuery.GuildRoster(masterId))

    return GuildView(
      info.guild?.let { guild ->
        Guild(
          name = guild.name,
          level = guild.level,
          motdTitle = guild.motdTitle,
          motdBody = guild.motdBody,
          emblemHash = guild.emblemHash,
          members = guild.members.map {
            GuildMember(it.masterId, it.name, it.level, it.rankTitle, it.isLeader, it.online, it.lastAreaName, it.lastSeenEpochMillis)
          },
        )
      }
    )
  }

  private inline fun <reified T : ZoneAnswer> ask(accountId: Long, query: ZoneQuery): T {
    return cache.get(accountId, query) { zone.ask(accountId, query).answerOrThrow<T>() }
  }
}
