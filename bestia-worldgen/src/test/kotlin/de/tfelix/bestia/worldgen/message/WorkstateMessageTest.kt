package de.tfelix.bestia.worldgen.message

import org.junit.Assert
import org.junit.Test

class WorkstateMessageTest {
  @Test
  fun ctor_ok() {
    WorkstateMessage("test", Workstate.MAP_PART_CONSUMED)
  }

  @Test
  fun getSource_ok() {
    val msg = WorkstateMessage("test", Workstate.MAP_PART_CONSUMED)
    Assert.assertEquals("test", msg.source)
  }

  @Test
  fun getState_ok() {
    val msg = WorkstateMessage("test", Workstate.MAP_PART_CONSUMED)
    Assert.assertEquals(Workstate.MAP_PART_CONSUMED, msg.state)
  }
}