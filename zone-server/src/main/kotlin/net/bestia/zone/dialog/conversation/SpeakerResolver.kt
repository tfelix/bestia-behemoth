package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.ai.knowledge.ChronicleNames
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.spawn.townsfolk.HouseholdPlacement
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * An entity, as the person standing in for it.
 *
 * Everything here is derived from `Townsfolk.identity` rather than from the entity, which is the whole
 * reason that component exists: townsfolk are built when a player comes near and destroyed when everyone
 * leaves, so the baker you spoke to this morning is a different entity by the afternoon and would
 * otherwise be a different person.
 */
@Service
class SpeakerResolver(
  private val world: WorldView,
  private val worldService: WorldService,
  private val sites: SettlementSiteIndex,
  private val placement: HouseholdPlacement,
) {

  /** @return null for anything that is not a townsperson, which is most entities. */
  fun of(entityId: EntityId): Speaker? {
    val identity = world.read { get(entityId, Townsfolk::class) }?.identity ?: return null

    val settlement = TownsfolkIdentity.settlementOf(identity)
    val household = TownsfolkIdentity.householdOf(identity)
    val member = TownsfolkIdentity.memberOf(identity)

    val summary = sites.siteOf(settlement)?.population ?: return null
    if (household !in 0 until summary.householdCount) return null

    val expanded = Households.one(summary, household)
    val person = expanded.members.getOrNull(member) ?: return null

    val seed = GenRng.hash(worldService.record.seed, identity, DIALOG_SALT)
    val culture = ChronicleNames.cultureOfSettlement(worldService.generated.world.chronicle, settlement)

    return Speaker(
      entityId = entityId,
      identity = identity,
      settlement = settlement,
      household = household,
      name = Names.townsperson(seed, culture),
      occupation = placement.occupationFor(expanded, person),
      // A child keeps no trade whatever the household does, exactly as `occupationFor` decides.
      business = expanded.business
        .takeIf { it >= 0 && person.kinship != Kinship.CHILD }
        ?.let { BusinessCatalogue.ALL[it].id },
      member = person,
      seed = seed,
    )
  }

  private companion object {
    /** One salt for everything a conversation derives, so a townsperson's voice is not their AI's. */
    const val DIALOG_SALT = 0xD1A706L
  }
}
