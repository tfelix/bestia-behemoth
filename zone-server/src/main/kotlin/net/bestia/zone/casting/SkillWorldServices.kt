package net.bestia.zone.casting

import net.bestia.zone.casting.sense.EchoSensing
import net.bestia.zone.casting.sense.ResourceSensing
import net.bestia.zone.casting.camp.BaseCamps
import net.bestia.zone.prop.TreePlanting
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.item.enchant.GroundItemDispel
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.ecs.effects.AreaEffectSpawner
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ForcedMovement
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.spoor.SpoorService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Component

/**
 * The services a [SkillWorld] delegates to, collected once so a per-cast [SkillWorld] costs one reference
 * rather than seven.
 *
 * A service belongs here rather than in a script's own constructor when its methods need a world to work
 * against - that is the whole reason it cannot be reached from a script directly. A service that needs no
 * world (a config, a calculator) a script may inject itself.
 *
 * Everything here is a singleton with no dependency back on the ECS `World` bean, which is what keeps it
 * out of the boot cycle described on [SkillWorld].
 */
@Component
class SkillWorldServices(
  val aoi: EntityAOIService,
  val structures: PlayerStructureService,
  val areaEffectSpawner: AreaEffectSpawner,
  val groundFire: net.bestia.zone.ground.fire.GroundFireService,
  val statusEffects: StatusEffectService,
  val messages: OutMessageProcessor,
  val crafting: RecipeOffering,
  val spoor: SpoorService,
  val chunkStream: ChunkStreamInbox,
  val lootItems: LootItemEntitySpawner,
  val speech: Speech,
  val forcedMovement: ForcedMovement,
  val castCancel: CastCancelService,
  val gear: GearOperations,
  val groundItemDispel: GroundItemDispel,
  val strikes: SkillStrikes? = null,
  val planting: TreePlanting? = null,
  val camps: BaseCamps? = null,
  val resourceSensing: ResourceSensing? = null,
  val echoSensing: EchoSensing? = null,
  val stalls: StallPitching? = null,
)
