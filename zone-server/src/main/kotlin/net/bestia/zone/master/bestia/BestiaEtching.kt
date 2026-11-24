package net.bestia.zone.master.bestia

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.crafting.EssenceEtching
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
 * Runic Etching on one of the etcher's own Bestias, called out and standing at the Carving Table. A Bestia in
 * storage is not in the world, so it cannot be picked yet.
 */
@Component
class BestiaEtching(
  private val catalogue: BestiaCatalogue,
  private val itemTemplates: ItemTemplateRegistry,
  private val structures: PlayerStructureService,
  private val loss: PlayerBestiaLoss,
) : EssenceEtching {

  override fun refusal(world: World, masterId: Long, bestiaId: EntityId, gemItemId: Long): OpError? {
    val tier = tierOf(gemItemId) ?: return OpError.CRAFT_NOT_POSSIBLE
    val ownedByEtcher = world.get(bestiaId, OwnedBestia::class)?.masterId == masterId
    val alive = world.get(bestiaId, Dead::class) == null
    val level = world.get(bestiaId, Level::class)?.level ?: 0

    // The window greys these out, so only a stale or forged request gets this far.
    if (!ownedByEtcher || !alive || level < tier.minBestiaLevel || !hasEssence(world, bestiaId)) {
      return OpError.CRAFT_NOT_POSSIBLE
    }

    val position = world.get(bestiaId, Position::class)?.toVec3L() ?: return OpError.CRAFT_NOT_POSSIBLE
    if (structures.stationNear(world, position, StaticEntityKind.CARVING_TABLE) == null) {
      return OpError.CRAFT_BESTIA_NOT_AT_TABLE
    }

    return null
  }

  override fun bind(world: World, bestiaId: EntityId, gemItemId: Long): Essence? {
    val tier = tierOf(gemItemId) ?: return null
    val speciesId = speciesOf(world, bestiaId) ?: return null
    if (world.get(bestiaId, OwnedBestia::class) == null) return null

    loss.lose(world, bestiaId)
    return Essence(bestiaId = speciesId, tier = tier)
  }

  private fun tierOf(gemItemId: Long): GemTier? = itemTemplates.identifierOf(gemItemId)?.let(GemTier::ofCutGem)

  private fun speciesOf(world: World, bestiaId: EntityId): Long? = world.get(bestiaId, EntityVisual::class)?.id

  /** Bosses have no essence yet. */
  private fun hasEssence(world: World, bestiaId: EntityId): Boolean {
    val speciesId = speciesOf(world, bestiaId) ?: return false

    return catalogue.find(speciesId)?.essence != null
  }
}
