package net.bestia.zone.faction.envoy

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Faction
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * The three Envoys at the temple door of every town and city, where masters ask to join an Order. Not stored:
 * every boot raises them again, so they follow the world they stand in. The species are non-combatants.
 */
@Service
class Envoys(
  private val sites: SettlementSiteIndex,
  private val placeNames: PlaceNameService,
  private val bestias: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
) {

  /** Every stand, in a fixed order. Read off the tick by the Novitiate, so built once and never changed. */
  val stands: List<EnvoyStand> by lazy { findStands() }

  /** Boot only. */
  fun raiseAll(world: World) {
    for (stand in stands) {
      val id = spawner.spawnMob(world, bestias.byIdentifier(speciesOf(stand.faction)).id, stand.position, persistent = false)
      world.add(id, FactionEnvoy(stand.faction, stand.settlementIndex))
      world.add(id, ScriptComponent(checkNotNull(EnvoyScript::class.simpleName)))
    }

    LOG.info { "Raised ${stands.size} Envoys in ${stands.size / Faction.entries.size} towns" }
  }

  fun standOf(faction: Faction, settlementIndex: Int): EnvoyStand? {
    return stands.firstOrNull { it.faction == faction && it.settlementIndex == settlementIndex }
  }

  /** The stand of [faction] nearest to [settlementIndex] in another town, for the pilgrimage. */
  fun nearestElsewhere(faction: Faction, settlementIndex: Int): EnvoyStand? {
    val home = standOf(faction, settlementIndex) ?: return null

    return stands
      .filter { it.faction == faction && it.settlementIndex != settlementIndex }
      .minByOrNull { it.position.distance(home.position) }
  }

  private fun findStands(): List<EnvoyStand> {
    return sites.sitesOf(ENVOY_TIERS)
      .sortedBy { it.index }
      .flatMap(::standsIn)
  }

  /** Side by side before the temple door, facing out; the market or the centre in a town without a temple. */
  private fun standsIn(site: SettlementSite): List<EnvoyStand> {
    val building = site.buildingsOf(BuildingFunction.TEMPLE).firstOrNull()
      ?: site.buildingsOf(BuildingFunction.MARKET).firstOrNull()
    val door = building?.let(sites::doorstepOf) ?: sites.standingAt(site.centre)
    val townName = placeNames.resolve(door.x, door.y).name

    return Faction.entries.mapIndexed { i, faction ->
      val sideways = (i - 1) * SPACING
      EnvoyStand(faction, site.index, townName, Vec3L(door.x + sideways, door.y, door.z))
    }
  }

  private fun speciesOf(faction: Faction): String {
    return "${faction.name.lowercase()}_envoy"
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    val ENVOY_TIERS = setOf(SettlementTier.TOWN, SettlementTier.CITY)

    /** Position units between two Envoys, enough to click each. */
    const val SPACING = 2L
  }
}
