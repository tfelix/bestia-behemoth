package net.bestia.zoneserver.script

import org.junit.jupiter.api.Test
import java.io.InputStreamReader
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngineManager

/**
 * playground for scripts. when scripting properly works it can be removed.
 */
class ScriptTest {

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
  }
}