package net.bestia.zone.bestia

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.GivenExp
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class BestiaEntityFactory(
  @Lazy
  private val zoneServer: ZoneServer,
  private val bestiaRepository: BestiaRepository
) {

  fun createMobEntity(
    bestiaId: Long,
    pos: Vec3L,
  ): EntityId {
    LOG.debug { "Spawning mob bestia $bestiaId on $pos" }

    return zoneServer.addEntityWithWriteLock { entity ->
      entity.addAll(
        Position.fromVec3(pos),
        GivenExp(200),
        BestiaVisual(bestiaId.toInt()),
        Health(10, 10),
        Speed(),
        IsDirty
      )
    }
  }

  fun createMobEntity(
    identifier: String,
    pos: Vec3L,
  ): EntityId {
    val bestia = bestiaRepository.findByIdentifierOrThrow(identifier)

    return createMobEntity(
      bestiaId = bestia.id,
      pos,
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}