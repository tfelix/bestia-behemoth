package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.bnet.proto.TownsfolkVisualComponentSMSGProto

/**
 * Which body a townsperson is drawn with.
 *
 * Coarse on purpose, and a body rather than an age: the client picks a model from it, so a distinction
 * it cannot draw is one nobody can see. What separates the two today is the one the rest of this package
 * already draws - a child keeps no trade, and `HouseholdPlacement.occupationFor` decides it the same way.
 */
enum class TownsfolkBody {
  ADULT,
  CHILD;

  fun toBnet(): TownsfolkVisualComponentSMSGProto.TownsfolkBody {
    return when (this) {
      ADULT -> TownsfolkVisualComponentSMSGProto.TownsfolkBody.ADULT
      CHILD -> TownsfolkVisualComponentSMSGProto.TownsfolkBody.CHILD
    }
  }
}
