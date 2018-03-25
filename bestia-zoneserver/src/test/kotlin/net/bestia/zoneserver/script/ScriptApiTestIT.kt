package net.bestia.zoneserver.script

import net.bestia.entity.EntityService
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.SimpleScriptEnv
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestZoneConfiguration::class)
class ScriptApiTestIT {

  @Mock
  private lateinit var entityService: EntityService

  @Autowired
  private lateinit var scriptCache: ScriptCache

  private lateinit var scriptRootApi: ScriptRootApi
  private lateinit var scriptExecutionService: ScriptExecutionService

  @Before
  fun setup() {
    scriptRootApi = ScriptRootApi(entityService)
    scriptExecutionService = ScriptExecutionService(scriptRootApi)
  }

  @Test
  fun test_entity() {
    val env = SimpleScriptEnv()
    val script = scriptCache.getScript("api/rootTest.js")
    scriptExecutionService.execute("main", script, env)
  }

  fun test_root() {
    val env = SimpleScriptEnv()
    val script = scriptCache.getScript("api/rootTest.js")
    scriptExecutionService.execute("main", script, env)
  }

  fun test_script() {
    val env = SimpleScriptEnv()
    val script = scriptCache.getScript("api/rootTest.js")
    scriptExecutionService.execute("main", script, env)
  }
}
