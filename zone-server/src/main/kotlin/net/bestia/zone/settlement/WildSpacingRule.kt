package net.bestia.zone.settlement

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlacementRule
import net.bestia.zone.prop.StructureNeighbourhood
import org.springframework.stereotype.Component

/**
 * Outside every settlement, keeps a little room between different masters' structures. A master's own camp may
 * stand close together, and a town is built as densely as its builders like.
 */
@Component
class WildSpacingRule(
  private val townBuildingRule: TownBuildingRule,
  private val neighbourhood: StructureNeighbourhood,
) : PlacementRule {

  override fun refusal(kind: StaticEntityKind, ownerMasterId: Long, at: Vec3L): OpError? {
    if (townBuildingRule.isInSettlement(at)) return null

    val isCrowded = neighbourhood.within(at, BuildingPlaces.WILD_SPACING_METRES)
      .any { it.ownerMasterId != ownerMasterId }

    return if (isCrowded) OpError.STRUCTURE_TOO_CLOSE else null
  }
}
