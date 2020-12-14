package net.bestia.ai.blackboard

import net.bestia.ai.sensor.Sensor

class GlobalBlackboardManager(
    private val sensors: List<Sensor>,
    private val blackboardRepository: BlackboardRepository
) {

  private var lastTickedSensor: Int = 0
  private val globalBlackboard: Blackboard

  init {
    globalBlackboard = blackboardRepository.findById(GLOBAL_BOARD_ID)
        ?: Blackboard(GLOBAL_BOARD_ID).also { blackboardRepository.save(it) }
  }

  fun tick(maxTimeBudgetMs: Long = 1000) {
    var runtimeMs = 0L

    for (sensor in sensors.drop(lastTickedSensor)) {
      if (runtimeMs >= maxTimeBudgetMs) {
        break
      }
      runtimeMs += measureRuntime { sensor.detect(globalBlackboard) }
      lastTickedSensor++
    }

    lastTickedSensor = 0
  }

  companion object {
    private const val GLOBAL_BOARD_ID = "global"
  }
}