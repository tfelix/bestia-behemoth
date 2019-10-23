package de.tfelix.bestia.worldgen.message

/**
 * Enum to describe the current workstate.
 *
 * @author Thomas Felix
 */
enum class Workstate {

  /**
   * Configuration object was received from the master.
   */
  RECEIVED_CONFIG,

  /**
   * Noise for this map part was generated.
   */
  MAP_PART_CONSUMED,

  /**
   * Send if the workload has executed.
   */
  WORKLOAD_DONE,

  /**
   * There was an error during the workload execution. Data might be in an
   * inconsistent state.
   */
  WORKLOAD_ERROR
}
