package net.bestia.zone.breeding

import net.bestia.zone.util.EntityId

/** The two bestias a farm breeds, with the levels their clutch was timed for. */
data class BreedingPair(
  val motherBestiaId: Long,
  val motherEntityId: EntityId,
  val motherLevel: Int,
  val fatherBestiaId: Long,
  val fatherEntityId: EntityId,
  val fatherLevel: Int,
) {

  val entityIds: List<EntityId>
    get() {
      return listOf(motherEntityId, fatherEntityId)
    }
}
