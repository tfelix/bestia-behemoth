package net.bestia.zone.fishing

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.item.script.ItemScript
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.treasure.TreasureCacheRegistry
import net.bestia.zone.treasure.TreasureMapService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Breaking the wax on a sealed bottle. The glass is kept, empty, for the alchemist. The paper inside is mostly a
 * treasure map to a cache still buried somewhere near; a bottle that drifted from nowhere anyone digs holds a
 * keepsake of the sea instead.
 */
@Component
class SealedBottleScript(
  private val items: ItemTemplateRegistry,
  private val caches: TreasureCacheRegistry,
  private val maps: TreasureMapService,
  private val random: Random = ThreadLocalRandomSource,
) : ItemScript {

  override val itemId: Long = ITEM_ID

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val owner = ItemOwner.of(world, userId) ?: return false
    val bottle = items.idOf(EMPTY_BOTTLE) ?: return false
    val grants = mutableListOf(CreateItemIntent(bottle, 1))

    val drawn = random.nextDouble() < MAP_CHANCE && drawMap(world, userId, owner.masterId)
    if (!drawn) {
      val keepsake = if (random.nextDouble() < PEARL_SHARE) SEA_PEARL else CORAL
      items.idOf(keepsake)?.let { grants.add(CreateItemIntent(it, 1)) }
    }

    // Added to whatever the entity is already due this tick, because a second intent would replace the first.
    val due = world.get(userId, CreateItemsIntent::class)?.grants.orEmpty()
    world.add(userId, CreateItemsIntent(due + grants))

    return true
  }

  /** Charts the nearest cache still buried, drawn loosely: whoever wrote it was no surveyor. */
  private fun drawMap(world: World, userId: EntityId, masterId: Long): Boolean {
    val position = world.get(userId, Position::class)?.toVec3L() ?: return false
    val accountId = world.get(userId, Account::class)?.accountId ?: return false
    val cache = caches.buriedNear(position.x.toDouble(), position.y.toDouble(), SEARCH_RADIUS_METRES).firstOrNull()
      ?: return false

    // Anywhere in the circle, evenly, so the cache is not likelier near its middle.
    val offset = MAP_RADIUS_METRES * sqrt(random.nextDouble())
    val angle = 2.0 * PI * random.nextDouble()
    maps.draw(
      userId, masterId, accountId, cache,
      cache.x - offset * cos(angle), cache.y - offset * sin(angle), MAP_RADIUS_METRES,
      spendsBlank = false,
    )

    return true
  }

  companion object {
    /** `sealed_bottle` in items.yml. */
    const val ITEM_ID = 193L

    const val MAP_CHANCE = 0.7
    const val PEARL_SHARE = 0.6
    const val SEARCH_RADIUS_METRES = 3_000.0
    const val MAP_RADIUS_METRES = 60.0

    private const val EMPTY_BOTTLE = "empty_bottle"
    private const val SEA_PEARL = "sea_pearl"
    private const val CORAL = "coral"
  }
}
