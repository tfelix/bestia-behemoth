package de.tfelix.bestia.worldgen

interface ClientApi {
  fun sendToClient(startWorkload: StartWorkload)
  val numberOfWorkers: Int
}