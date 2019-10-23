package de.tfelix.bestia.worldgen

interface MapMasterCallbacks {
  /**
   * Called if the current workload has finished processing on all nodes. A
   * new workload can be put together depending on the returned result by the
   * nodes.
   */
  fun onWorkloadFinished(master: MapGeneratorMaster, label: String)

  /**
   * The nodes have created the noise seeds of the map and are now able to
   * process the workloads to process either the noise maps itself or to
   * transform the noise into a map data point containing actual map data.
   */
  fun onNoiseGenerationFinished(master: MapGeneratorMaster)
}
