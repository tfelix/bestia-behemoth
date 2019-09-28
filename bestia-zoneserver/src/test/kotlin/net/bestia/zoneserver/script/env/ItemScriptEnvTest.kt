package net.bestia.zoneserver.script.env

import net.bestia.model.geometry.Vec3
import org.junit.Assert
import org.junit.Test
import javax.script.SimpleBindings

class ItemScriptEnvTest {

  private val targetPos = Vec3(10, 10)
  private val userId = 10L
  private val targetId = 10L

  @Test
  fun test_item_bindings_target_entity() {
    val env = ItemScriptEnv(userId, targetId)
    val bindings = SimpleBindings()
    env.setupEnvironment(bindings)
    Assert.assertEquals(setOf("SELF", "TARGET_ENTITY", "TARGET_POSITION"), bindings.keys)
    Assert.assertNull(bindings["TARGET_POSITION"])
  }

  @Test
  fun test_item_bindings_target_position() {
    val env = ItemScriptEnv(userId, targetPos)
    val bindings = SimpleBindings()
    env.setupEnvironment(bindings)
    Assert.assertEquals(setOf("SELF", "TARGET_ENTITY", "TARGET_POSITION"), bindings.keys)
    Assert.assertNull(bindings["TARGET_ENTITY"])
  }
}