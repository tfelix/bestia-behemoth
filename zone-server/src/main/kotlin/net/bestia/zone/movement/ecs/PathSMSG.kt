package net.bestia.zone.movement.ecs

import net.bestia.bnet.proto.PathComponentSMSGProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.geometry.DeltaPaths
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

/**
 * The waypoints an entity is walking, or - with an empty [path] - the notification that it has
 * stopped, at [stopPosition]. See [Path] for why this is sent once per walk rather than per step.
 */
data class PathSMSG(
  override val entityId: Long,
  val path: List<Vec3L>,
  /**
   * Where the entity halted, on a stop only - null when [path] is non-empty, and on the (impossible in
   * practice) stop for an entity carrying no [Position] at all.
   */
  val stopPosition: Vec3L? = null,
  /** Progress along the first segment, 0..1; see [Position.stepProgress]. */
  val startOffset: Float = 0f,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val pathComp = PathComponentSMSGProto.PathComponentSMSG.newBuilder()
      .setStartOffset(startOffset)

    if (path.isNotEmpty()) {
      pathComp.setPath(DeltaPaths.encode(path))
    }

    stopPosition?.let { pathComp.setStopPosition(it.toProto()) }

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setPath(pathComp))
  }

  private companion object {
    private fun Vec3L.toProto(): Vec3OuterClass.Vec3 = Vec3OuterClass.Vec3.newBuilder()
      .setX(x)
      .setY(y)
      .setZ(z)
      .build()
  }
}
