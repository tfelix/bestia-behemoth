package net.bestia.ai

import net.bestia.ai.behavior.tree.HasProperty
import net.bestia.ai.behavior.tree.PlanAction
import net.bestia.ai.behavior.tree.Sequence
import net.bestia.ai.blackboard.Blackboard
import net.bestia.ai.blackboard.BlackboardRepository
import net.bestia.ai.blackboard.GlobalBlackboardManager
import net.bestia.ai.sensor.Sensor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension


class GlobalClockSensor : Sensor {
  override fun detect(blackboard: Blackboard) {
    val currentTime = blackboard.getEntry("globalClock") as? Blackboard.Entry<Float> ?: makeEntry()

    currentTime.data = 0.1f

    blackboard.setEntry(currentTime)
  }

  private fun makeEntry(): Blackboard.Entry<Float> {
    return Blackboard.Entry.create("globalClock", 0.0f)
  }
}

@ExtendWith(MockitoExtension::class)
class SimpleMobAiTest {

  @Mock
  private lateinit var blackboardRepository: BlackboardRepository

  private lateinit var gbm: GlobalBlackboardManager

  @BeforeEach
  fun setup() {
    gbm = GlobalBlackboardManager(
        listOf(GlobalClockSensor()),
        blackboardRepository
    )

    val agentBlackboard = Blackboard("agent")

    // BHT
    val sleepSequence = Sequence(listOf(
        HasProperty(agentBlackboard, "rested") { it < 0.2 },
        PlanAction(agentBlackboard, "sleep")
    ))

    val hungerSequence = Sequence(listOf(
        HasProperty(agentBlackboard, "hunger") { it > 0.8 },
        // search food source
        PlanAction(agentBlackboard, "eat")
    ))
  }

  @Test
  fun `AI simulates a normal day behavior`() {
    gbm.tick()

  }
}