package net.bestia.zoneserver.script.env

import net.bestia.model.geometry.Point
import net.bestia.zoneserver.script.api.ScriptApi
import org.junit.Test
import org.mockito.Mock

class ItemScriptEnvTest {

  private val targetPos = Point(10, 10)
  private val userId = 10L
  private val targetId = 10L

  @Mock
  private lateinit var scriptApi: ScriptApi

  @Test
  fun test_item_bindings() {
    val env = ItemScriptEnv(userId, targetId)
  }
}