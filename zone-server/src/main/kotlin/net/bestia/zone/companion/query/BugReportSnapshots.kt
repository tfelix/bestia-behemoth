package net.bestia.zone.companion.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component as SpringComponent

/**
 * The zone's own view of a reporter's master and what stands around it, so a bug report holds the server's truth
 * next to the client's. Components are dumped field by field, because their shape is what a reader wants to check.
 */
@SpringComponent
class BugReportSnapshots(
  private val connections: ConnectionInfoService,
  private val world: WorldView,
  private val entities: EntityAOIService,
  private val worldService: WorldService,
) {

  private val json = jacksonObjectMapper()
    .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .also { it.configOverride(DirtyFlag::class.java).isIgnoredType = true }

  fun snapshot(correlationId: String, accountId: Long): BusReply {
    val masterId = connections.activeMasterIdOf(accountId)
      ?: return BusReply.of(correlationId, BusReply.Status.NOT_FOUND)
    val masterEntity = connections.getSelectedMasterEntityId(accountId)
    val activeEntity = connections.getActiveEntityId(accountId)

    val snapshot = world.read { snapshotOf(masterId, masterEntity, activeEntity) }

    return BusReply.ok(correlationId, ZoneAnswer.Snapshot(json.writeValueAsString(snapshot)))
  }

  private fun World.snapshotOf(masterId: Long, masterEntity: EntityId, activeEntity: EntityId): Map<String, Any?> {
    val activePosition = get(activeEntity, Position::class)?.toVec3L()
    val nearby = activePosition
      ?.let { entities.queryEntitiesInCube(it, nearbyCubeEdgeVoxels()) }
      .orEmpty()
      .filter { it != masterEntity && it != activeEntity }
      .take(MAX_NEARBY_ENTITIES)

    return linkedMapOf(
      "tick" to tickCount,
      "voxelSizeMetres" to worldService.config.voxelSize,
      "masterId" to masterId,
      "masterEntityId" to masterEntity,
      "activeEntityId" to activeEntity,
      "activePosition" to activePosition,
      "master" to componentsDumpOf(masterEntity),
      "activeEntity" to if (activeEntity != masterEntity) componentsDumpOf(activeEntity) else "the master",
      "nearbyWithinMetres" to NEARBY_METRES,
      "nearby" to nearby.associate { it.toString() to componentsDumpOf(it) },
    )
  }

  private fun World.componentsDumpOf(entity: EntityId): Map<String, Any?> {
    return componentsOf(entity).associate { (it::class.simpleName ?: "?") to dumpOf(it) }
  }

  /** The component's fields as JSON, or its `toString()` when they do not serialize; long ones are cut. */
  private fun dumpOf(component: Component): Any? {
    val fields = runCatching { json.writeValueAsString(component) }.getOrNull()
      ?: return component.toString().take(MAX_COMPONENT_CHARS)

    return if (fields.length <= MAX_COMPONENT_CHARS) json.readTree(fields) else fields.take(MAX_COMPONENT_CHARS) + "…"
  }

  private fun nearbyCubeEdgeVoxels(): Long {
    return (2 * NEARBY_METRES / worldService.config.voxelSize).toLong()
  }

  private companion object {
    const val NEARBY_METRES = 30.0
    const val MAX_NEARBY_ENTITIES = 40
    const val MAX_COMPONENT_CHARS = 4_000
  }
}
