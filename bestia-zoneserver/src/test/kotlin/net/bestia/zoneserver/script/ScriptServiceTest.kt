package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.mock
import net.bestia.zoneserver.Fixtures
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.exec.ItemScriptExec
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.InputStreamReader
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngineManager

/**
 * Validates the existing script for basic execution.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScriptServiceTest: BaseScriptTest() {
  @Throws(Exception::class)
  @Test
  @Disabled
  fun test() {
    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val scriptInput = this.javaClass.classLoader.getResourceAsStream("script/test.js")

    val scriptReader = InputStreamReader(scriptInput)

    val script = (engine as Compilable).compile(scriptReader)
    script.eval()
    val invocable = script.engine as Invocable
    invocable.invokeFunction("test")
  }

  private val userEntity = Entity(id = 100)

  @Test
  fun scriptValidation() {
    val exec = ItemScriptExec.Builder().apply {
      itemDbName = "apple"
      user = userEntity
    }.build()

    scriptService.execute(exec)
  }
}