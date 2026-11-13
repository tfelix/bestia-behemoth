package net.bestia.zone.treasure

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.battle.GearWear
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.mining.PickaxeMining
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.treasure.ecs.Digging
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Digging for treasure, one metre at a time. A dig over a cache carries on by itself until it reaches the cache;
 * a dig anywhere else stops after a metre with nothing found, so digging at random costs time and a tool. A pickaxe
 * on rock with no cache below mines instead, see [PickaxeMining].
 *
 * Tick thread only, like [TreasureCacheRegistry].
 */
@Service
class DigService(
  private val registry: TreasureCacheRegistry,
  private val loot: TreasureLoot,
  private val maps: TreasureMapService,
  private val relics: Relics,
  private val richness: TreasureRichness,
  private val chunkService: ChunkService,
  private val inbox: ChunkStreamInbox,
  private val gearWear: GearWear,
  private val items: ItemTemplateRegistry,
  private val skillIds: SkillIds,
  private val outMessageProcessor: OutMessageProcessor,
  private val pickaxeMining: PickaxeMining,
) {

  private val shovelId by lazy { items.idOf(SHOVEL) }
  private val pickaxeId by lazy { items.idOf(PICKAXE) }

  val excavationSkillId: Long? get() = skillIds[SkillId.M_EXCAVATION]

  fun begin(world: World, entityId: EntityId, accountId: Long, target: Vec3L) {
    val position = world.get(entityId, Position::class)?.toVec3L() ?: return
    val targetX = target.x + HALF_VOXEL
    val targetY = target.y + HALF_VOXEL

    val cache = registry.buriedNear(targetX, targetY, SPOT_REACH).firstOrNull { cache ->
      abs(cache.floor - target.z) <= SPOT_HEIGHT_REACH + dugMetresOf(cache)
    }
    if (horizontalDistance(position, targetX, targetY) > REACH + (cache?.let(::dugMetresOf) ?: 0.0)) {
      return refuse(accountId, OpError.DIG_TOO_FAR)
    }

    if (cache == null) {
      val ground = Vec3L(target.x, target.y, target.z - 1)
      if (toolIn(world, entityId) == DigTool.PICKAXE && toolFor(blockAt(ground)) == DigTool.PICKAXE) {
        return pickaxeMining.begin(world, entityId, accountId, target)
      }

      val trench = trenchToward(position, targetX, targetY, target.z.toDouble())
      startStep(world, entityId, accountId, null, trench, fromDepth = 0.0)
      return
    }

    val progress = registry.progressOf(cache.id)
    val trench = if (progress == null) {
      trenchToward(position, cache.x, cache.y, cache.floor)
    } else {
      TreasureTrench(cache.x, cache.y, cache.floor, progress.towardX, progress.towardY)
    }
    startStep(world, entityId, accountId, cache, trench, fromDepth = progress?.dugMetres ?: 0.0)
  }

  /** Takes out the metre [digging] was working on, then digs on or opens the cache. */
  fun finish(world: World, entityId: EntityId, digging: Digging) {
    if (toolIn(world, entityId) == null) return

    inbox.offerCarve(ChunkStreamInbox.Carve(digging.accountId, digging.trench.brushTo(digging.toDepth)))
    gearWear.wearWeapon(world, entityId, WEAR_PER_METRE)

    val cache = digging.cache ?: return refuse(digging.accountId, OpError.DIG_NOTHING_FOUND)
    registry.recordDig(cache.id, digging.toDepth, digging.trench.towardX, digging.trench.towardY)

    if (digging.toDepth < cache.depth) {
      startStep(world, entityId, digging.accountId, cache, digging.trench, digging.toDepth)
    } else {
      open(world, entityId, cache)
    }
  }

  private fun startStep(
    world: World,
    entityId: EntityId,
    accountId: Long,
    cache: TreasureCache?,
    trench: TreasureTrench,
    fromDepth: Double,
  ) {
    val held = toolIn(world, entityId) ?: return refuse(accountId, OpError.DIG_NEEDS_TOOL)
    val needed = toolFor(blockAt(trench.voxelBelow(fromDepth)))
    if (needed != null && needed != held) {
      return refuse(accountId, if (needed == DigTool.PICKAXE) OpError.DIG_NEEDS_PICKAXE else OpError.DIG_NEEDS_SHOVEL)
    }

    val toDepth = min(fromDepth + 1.0, cache?.depth ?: BLIND_DEPTH)
    val speed = 1.0 - TIME_CUT_PER_EXCAVATION_LEVEL * excavationLevelOf(world, entityId)
    val seconds = SECONDS_PER_METRE * (toDepth - fromDepth) * speed
    world.add(entityId, Digging(accountId, cache, trench, toDepth, seconds.toFloat()))
  }

  private fun open(world: World, entityId: EntityId, cache: TreasureCache) {
    registry.open(cache.id)
    maps.crumble(world, cache.id)

    val relic = if (cache.artifact >= 0) relics.unearth(cache.artifact) else null
    val contents = loot.roll(richness.of(cache), excavationLevelOf(world, entityId)) + listOfNotNull(relic)
    give(world, entityId, contents)
  }

  /** Added to whatever the entity is already due this tick, because a second intent would replace the first. */
  private fun give(world: World, entityId: EntityId, contents: List<CreateItemIntent>) {
    if (contents.isEmpty()) return

    val due = world.get(entityId, CreateItemsIntent::class)?.grants.orEmpty()
    world.add(entityId, CreateItemsIntent(due + contents))
  }

  private fun trenchToward(position: Vec3L, spotX: Double, spotY: Double, topZ: Double): TreasureTrench {
    val dx = position.x + HALF_VOXEL - spotX
    val dy = position.y + HALF_VOXEL - spotY
    val length = hypot(dx, dy)
    if (length < MIN_DIRECTION) return TreasureTrench(spotX, spotY, topZ, 1.0, 0.0)

    return TreasureTrench(spotX, spotY, topZ, dx / length, dy / length)
  }

  private fun dugMetresOf(cache: TreasureCache): Double {
    return registry.progressOf(cache.id)?.dugMetres ?: 0.0
  }

  private fun excavationLevelOf(world: World, entityId: EntityId): Int {
    val skillId = excavationSkillId ?: return 0
    return world.get(entityId, KnownSkills::class)?.levelOf(skillId) ?: 0
  }

  private fun toolIn(world: World, entityId: EntityId): DigTool? {
    val held = world.get(entityId, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) ?: return null
    if (held.isBroken) return null

    return when (held.itemId) {
      shovelId -> DigTool.SHOVEL
      pickaxeId -> DigTool.PICKAXE
      else -> null
    }
  }

  /** Null where either tool will do: air, water, or the bottom of a cave. */
  private fun toolFor(block: BlockType?): DigTool? {
    if (block == null || !block.solid || !block.carvable) return null

    return if (block in SOIL) DigTool.SHOVEL else DigTool.PICKAXE
  }

  private fun blockAt(voxel: Vec3L): BlockType? {
    val config = chunkService.config
    val localised = ChunkCoords.localise(config, voxel.x, voxel.y, voxel.z) ?: return null
    val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
    val id = chunkService.merged(chunkService.normalise(localised.chunk)).blocks[index].toInt() and 0xFF

    return BlockType.ofOrNull(id)
  }

  private fun horizontalDistance(position: Vec3L, x: Double, y: Double): Double {
    return hypot(position.x + HALF_VOXEL - x, position.y + HALF_VOXEL - y)
  }

  private fun refuse(accountId: Long, code: OpError) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private enum class DigTool { SHOVEL, PICKAXE }

  private companion object {
    const val SHOVEL = "shovel"
    const val PICKAXE = "pickaxe"

    const val SECONDS_PER_METRE = 20.0
    const val TIME_CUT_PER_EXCAVATION_LEVEL = 0.2
    const val WEAR_PER_METRE = 1

    /** How deep a dig goes where nothing is buried before the digger gives up. */
    const val BLIND_DEPTH = 1.0

    /** How far from the digger a spot may be, in metres, plus however deep its trench already is. */
    const val REACH = 4.0

    /** How far from the clicked tile a cache may lie and still be the one being dug for. */
    const val SPOT_REACH = 2.5
    const val SPOT_HEIGHT_REACH = 3.0

    const val HALF_VOXEL = 0.5
    const val MIN_DIRECTION = 0.1

    val SOIL = setOf(
      BlockType.GRAVEL, BlockType.SAND, BlockType.DIRT, BlockType.MUD, BlockType.GRASS, BlockType.DRY_GRASS,
      BlockType.SNOW, BlockType.BLIGHTED_GRASS, BlockType.BLIGHTED_DIRT, BlockType.BLIGHTED_SAND,
      BlockType.PACKED_EARTH
    )
  }
}
