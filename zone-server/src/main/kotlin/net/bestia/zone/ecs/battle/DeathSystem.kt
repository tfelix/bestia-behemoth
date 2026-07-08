package net.bestia.zone.ecs.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.status.GivenExp
import net.bestia.zone.ecs.bestia.BestiaVisual
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.EntityDiedEvent
import org.springframework.core.annotation.Order
import kotlin.math.floor
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(70)
class DeathSystem : System {

  override val reads: Set<KClass<out Component>> =
    setOf(Dead::class, GivenExp::class, TakenDamage::class, BestiaVisual::class, Position::class)

  override val writes: Set<KClass<out Component>> = setOf(Exp::class)

  override fun update(world: World, deltaTime: Float) {
    // Snapshot the dead entities first: iteration mutates other entities (exp) and destroys these.
    val dead = ArrayList<EntityId>()
    world.query(Dead::class).each { id -> dead.add(id) }

    for (id in dead) {
      LOG.debug { "Entity $id is dead" }
      val givenExp = world.getOrThrow(id, GivenExp::class).value
      val damageDealer = world.getOrThrow(id, TakenDamage::class).damagePercentages()

      assignExp(world, givenExp, damageDealer)

      val position = world.getOrThrow(id, Position::class).toVec3L()
      val lootBestiaId = world.get(id, BestiaVisual::class)?.id?.toLong()

      world.emit(EntityDiedEvent(entityId = id, position = position, lootBestiaId = lootBestiaId))

      world.destroy(id)
    }
  }

  private fun assignExp(
    world: World,
    givenExp: Int,
    damageDealer: Map<EntityId, Float>,
  ) {
    LOG.debug { "Distribute $givenExp EXP to $damageDealer" }

    damageDealer.forEach { (entityId, percent) ->
      if (!world.isAlive(entityId)) return@forEach
      val receivedExp = floor(givenExp * percent).toInt()

      val exp = world.get(entityId, Exp::class) ?: world.add(entityId, Exp())
      exp.value += receivedExp

      world.markChanged<Exp>(entityId)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
