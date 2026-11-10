package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.prop.kit.BuildingKit
import net.bestia.zone.settlement.TownService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Trade Post Owner skill (`skills.yml` id 58): uses up an auction house kit and starts an auction house where
 * it is aimed. The house belongs to the caster, who may own as many as the skill's level.
 */
@Component
class TradePostOwner(
  private val towns: TownService,
  private val offices: PostOffices,
  private val houses: PlayerHouses,
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService), BuildingKit {

  override val kind = StaticEntityKind.AUCTION_HOUSE
  override val kitItemId = KIT_ITEM_ID
  override val buildSeconds = BUILD_SECONDS

  /** Checked where the caster stands: the house goes up within two tiles of it, so in the same town. */
  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val masterId = world.get(casterId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return OpError.REQUEST_REFUSED
    val hasKit = world.get(casterId, Inventory::class)?.getItems()?.any { it.itemId == KIT_ITEM_ID } == true

    return when {
      !hasKit -> OpError.TRADE_POST_NEEDS_KIT
      houses.ownedBy(masterId).size >= skillLevel -> OpError.TRADE_POST_LIMIT
      else -> siteRefusal(at)
    }
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return super.isCastPossible(ctx) && siteRefusal(ctx.aimedAt) == null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val masterId = ctx.world.masterIdOf(ctx.casterId) ?: return null
    val accountId = ctx.world.accountIdOf(ctx.casterId) ?: return null
    val structureId = ctx.world.beginStructure(StaticEntityKind.AUCTION_HOUSE, ctx.aimedAt, BUILD_SECONDS) ?: return null

    houses.register(structureId, masterId, accountId, AuctionFees.MIN_OWNER_FEE_PERCENT)

    return null
  }

  private fun siteRefusal(at: Vec3L): OpError? {
    return towns.buildingRefusal(at, StaticEntityKind.AUCTION_HOUSE)
      ?: if (offices.playerOfficeAt(at) == null) OpError.TRADE_POST_NEEDS_POST_OFFICE else null
  }

  companion object {
    /** `items.yml`'s auction_house_kit, which `skills.yml` also names as this skill's reagent. */
    const val KIT_ITEM_ID = 151L

    /** 60 minutes of work: the most expensive building a town has. */
    const val BUILD_SECONDS = 3600f
  }
}
