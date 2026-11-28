package net.bestia.zone.ai.ecs

/** Whether the station or shaft a bestia works waits for its master. A port: crafting keeps the stations' progress. */
fun interface WorkplacePauses {
  fun waitsForMaster(structureId: Long): Boolean
}
