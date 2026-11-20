package net.bestia.zone.prop

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L

/** One reason a player structure may not go up where it was aimed. Slices that know about towns implement it. */
interface PlacementRule {
  fun refusal(kind: StaticEntityKind, ownerMasterId: Long, at: Vec3L): OpError?
}
