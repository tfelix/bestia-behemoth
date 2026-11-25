package net.bestia.zone.inn

import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.BestiaTag
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.respawn.ecs.Respawn
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.ceil

/** What an inn charges to restore a master's bestias out in the world, and the restoring. Tick thread only. */
@Component
class InnTending {

  data class Line(
    val entityId: EntityId,
    val name: String?,
    val bestiaId: Long,
    val price: Int,
    val fallen: Boolean,
  )

  /** Only the bestias that lack something: a whole one costs nothing and is not listed. */
  fun quote(world: World, masterId: Long): List<Line> {
    return OwnedBestia.ownedBy(world, masterId)
      .mapNotNull { lineOf(world, it.entityId) }
      .filter { it.price > 0 }
      .sortedBy { it.entityId }
  }

  /** A fallen bestia gets up at the inn's [door]; a standing one is restored where it is. */
  fun tend(world: World, lines: List<Line>, door: Vec3L) {
    for (line in lines) {
      if (line.fallen) {
        world.add(line.entityId, Respawn(door, restored = true))
      } else {
        world.get(line.entityId, Health::class)?.let { it.current = it.max }
        world.get(line.entityId, Mana::class)?.let { it.current = it.max }
      }
    }
  }

  private fun lineOf(world: World, id: EntityId): Line? {
    val level = world.get(id, Level::class)?.level ?: return null
    val health = world.get(id, Health::class) ?: return null
    val fallen = world.has(id, Dead::class)
    val price = priceOf(level, missingShare(health), world.get(id, Mana::class)?.let(::missingShare) ?: 0.0, fallen)

    return Line(id, world.get(id, BestiaTag::class)?.name, world.get(id, EntityVisual::class)?.id ?: 0L, price, fallen)
  }

  private fun missingShare(pool: CurMax): Double {
    return if (pool.max == 0) 0.0 else (pool.max - pool.current).toDouble() / pool.max
  }

  companion object {
    /** Coins per level for restoring every missing point. See the inn section of the settlements design page. */
    const val HEAL_PER_LEVEL = 2

    /** Coins per level for getting a fallen bestia up, on top of its full heal. */
    const val REVIVE_PER_LEVEL = 5

    fun priceOf(level: Int, missingHealth: Double, missingMana: Double, fallen: Boolean): Int {
      if (fallen) {
        return (REVIVE_PER_LEVEL + HEAL_PER_LEVEL) * level
      }
      val missing = (missingHealth + missingMana) / 2

      return ceil(HEAL_PER_LEVEL * level * missing).toInt()
    }
  }
}
