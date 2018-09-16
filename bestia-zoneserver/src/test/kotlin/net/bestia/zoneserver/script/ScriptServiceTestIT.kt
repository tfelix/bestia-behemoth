package net.bestia.zoneserver.script

import net.bestia.zoneserver.entity.EntityService
import net.bestia.entity.factory.MobFactory
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.config.StaticConfig
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.GlobalEnv
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestZoneConfiguration::class)
class ScriptServiceTestIT {

  @TestConfiguration
  class TestConfig {

    @Mock
    private lateinit var entityService: EntityService

    @Mock
    private lateinit var mobFactory: MobFactory

    @Bean
    fun globEnv(): GlobalEnv {
      return GlobalEnv(
              config = StaticConfig(
                      serverVersion = "1337",
                      scriptDir = "classpath:script"
              ),
              api = ScriptRootApi(entityService, mobFactory)
      )
    }
  }

  @Autowired
  private lateinit var scriptService: ScriptService

  @Test
  fun test_entity() {
    scriptService.callScriptMainFunction("api/rootTest.js")
  }
}
