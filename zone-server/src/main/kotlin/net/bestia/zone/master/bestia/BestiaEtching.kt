package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.crafting.EssenceEtching
import net.bestia.zone.crafting.EtchingOffer
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.essence.Essence
import net.bestia.zone.item.essence.GemTier
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Runic Etching on a Bestia called out and standing at the Carving Table: one of the etcher's own, or one its owner
 * offered there, see [EtchingOffers]. A Bestia in storage is not in the world, so it cannot be picked yet.
 */
@Component
class BestiaEtching(
  private val catalogue: BestiaCatalogue,
  private val itemTemplates: ItemTemplateRegistry,
  private val structures: PlayerStructureService,
  private val loss: PlayerBestiaLoss,
  private val offers: EtchingOffers,
) : EssenceEtching {

  override fun refusal(world: World, masterId: Long, bestiaId: EntityId, gemItemId: Long): OpError? {
    val tier = tierOfGem(gemItemId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val ownedByEtcher = world.get(bestiaId, OwnedBestia::class)?.masterId == masterId
    val offered = offers.isOffered(world, bestiaId) && offers.ownerOf(world, bestiaId) != null
    val alive = world.get(bestiaId, Dead::class) == null
    val level = world.get(bestiaId, Level::class)?.level ?: 0

    // The window greys these out, so only a stale or forged request gets this far.
    val species = speciesOf(world, bestiaId)
    if (!(ownedByEtcher || offered) || !alive || species == null || !fits(species, level, tier)) {
      return OpError.CRAFT_NOT_POSSIBLE
    }

    val position = world.get(bestiaId, Position::class)?.toVec3L() ?: return OpError.CRAFT_NOT_POSSIBLE
    if (structures.stationNear(world, position, StaticEntityKind.CARVING_TABLE) == null) {
      return OpError.CRAFT_BESTIA_NOT_AT_TABLE
    }

    return null
  }

  override fun bind(world: World, bestiaId: EntityId, gemItemId: Long): Essence? {
    val tier = tierOfGem(gemItemId) ?: return null
    val speciesId = speciesOf(world, bestiaId) ?: return null
    if (world.get(bestiaId, OwnedBestia::class) == null) return null

    loss.lose(world, bestiaId)
    return Essence(bestiaId = speciesId, tier = tier)
  }

  override fun ownerOf(world: World, bestiaId: EntityId): EntityId? = offers.ownerOf(world, bestiaId)

  override fun offersAt(world: World, tablePosition: Vec3L, etcherMasterId: Long): List<EtchingOffer> {
    return offers.offersAt(world, tablePosition, etcherMasterId)
  }

  fun tierOfGem(gemItemId: Long): GemTier? = itemTemplates.identifierOf(gemItemId)?.let(GemTier::ofCutGem)

  private fun speciesOf(world: World, bestiaId: EntityId): Long? = world.get(bestiaId, EntityVisual::class)?.id

  /**
   * Whether a Bestia of [speciesId] at [level] may fill a [tier] gem, wherever it is: called out or in storage. A boss
   * fills only the gem of its level.
   */
  fun fits(speciesId: Long, level: Int, tier: GemTier): Boolean {
    val species = catalogue.find(speciesId) ?: return false
    val bossGem = if (species.boss || species.worldBoss) GemTier.ofBossLevel(species.referenceLevel) else null

    return species.essence != null && level >= tier.minBestiaLevel && (bossGem == null || bossGem == tier)
  }
}
