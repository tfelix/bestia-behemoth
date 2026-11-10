package net.bestia.zone.breeding

import net.bestia.bnet.proto.BreedingFarmSmsgProto.BreedingFarmPhase

/** Where a Breeding Farm is in its one job. The order is the wire order. */
enum class FarmPhase {
  IDLE,

  /** The pair walks to the farm. The bundle is held, not used yet. */
  GATHERING,
  BREEDING,

  /** The timer ended and the egg is being rolled off the tick. */
  LAYING,

  /** An egg waits to be taken; no clutch starts until it is. */
  EGG_READY;

  fun toBnet(): BreedingFarmPhase {
    return BreedingFarmPhase.forNumber(ordinal)
  }
}
