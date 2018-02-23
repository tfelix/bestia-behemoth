package net.bestia.zoneserver.script

import net.bestia.zoneserver.script.env.BaseScriptEnvironment
import net.bestia.zoneserver.script.exec.ScriptFunctionExecutor
import org.junit.Test
import org.mockito.Mock

class ScriptServiceTest {

  @Mock
  lateinit var scriptApi : ScriptApi

  @Test
  fun callScriptTest() {
    val scriptEnv = BaseScriptEnvironment(scriptApi)

    // Aufbau des environments.

    // Besonderer Call einer bestimmten funktion.

    val funExecutor = ScriptFunctionExecutor("main", scriptEnv, )
  }
}