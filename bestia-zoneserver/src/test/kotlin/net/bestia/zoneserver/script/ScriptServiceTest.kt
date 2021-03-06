package net.bestia.zoneserver.script

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.exec.ItemScriptExec
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

  /*
  Only there for testing calling of scripts
  @Throws(Exception::class)
  @Test
  fun test() {
    val engine = ScriptEngineManager().getEngineByName("nashorn")
    val scriptInput = this.javaClass.classLoader.getResourceAsStream("script/test.js")

    val scriptReader = InputStreamReader(scriptInput)

    val script = (engine as Compilable).compile(scriptReader)
    script.eval()
    val invocable = script.engine as Invocable
    invocable.invokeFunction("test")
  }*/

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