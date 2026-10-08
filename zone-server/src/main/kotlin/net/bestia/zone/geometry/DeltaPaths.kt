package net.bestia.zone.geometry

import net.bestia.bnet.proto.DeltaPathProto
import net.bestia.bnet.proto.Vec3OuterClass

/** A path of tile steps on the wire: the first waypoint, then each following one as its difference to the last. */
object DeltaPaths {

  private const val AXES = 3

  fun encode(path: List<Vec3L>): DeltaPathProto.DeltaPath {
    val encoded = DeltaPathProto.DeltaPath.newBuilder()
    if (path.isEmpty()) {
      return encoded.build()
    }

    encoded.setFirst(path.first().toProto())
    path.zipWithNext { from, to ->
      encoded.addDeltas(Math.toIntExact(to.x - from.x))
      encoded.addDeltas(Math.toIntExact(to.y - from.y))
      encoded.addDeltas(Math.toIntExact(to.z - from.z))
    }

    return encoded.build()
  }

  /** An incomplete trailing step is dropped; the move handler checks every step anyway. */
  fun decode(encoded: DeltaPathProto.DeltaPath): List<Vec3L> {
    if (!encoded.hasFirst()) {
      return emptyList()
    }

    val first = encoded.first
    var current = Vec3L(first.x, first.y, first.z)
    val path = ArrayList<Vec3L>(1 + encoded.deltasCount / AXES)
    path.add(current)

    for (i in 0 until encoded.deltasCount / AXES * AXES step AXES) {
      current = Vec3L(
        current.x + encoded.getDeltas(i),
        current.y + encoded.getDeltas(i + 1),
        current.z + encoded.getDeltas(i + 2)
      )
      path.add(current)
    }

    return path
  }

  private fun Vec3L.toProto(): Vec3OuterClass.Vec3 {
    return Vec3OuterClass.Vec3.newBuilder().setX(x).setY(y).setZ(z).build()
  }
}
