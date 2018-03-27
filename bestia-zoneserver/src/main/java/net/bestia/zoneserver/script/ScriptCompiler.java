package net.bestia.zoneserver.script;

import net.bestia.zoneserver.script.env.GlobalEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

/**
 * The ScriptCache is responsible for reading and compiling all the scripts for
 * the system.
 *
 * @author Thomas Felix
 */
@Component
public class ScriptCompiler {

  private static final Logger LOG = LoggerFactory.getLogger(ScriptCompiler.class);

  private final ScriptEngine engine;

  public ScriptCompiler(GlobalEnv globalEnv) {
    this.engine = new ScriptEngineManager().getEngineByName("nashorn");
    LOG.info("Starting script engine: {} (version {}).",
            engine.getFactory().getEngineName(),
            engine.getFactory().getEngineVersion());

    final Bindings bindings = engine.createBindings();
    globalEnv.setupEnvironment(bindings);
    engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE);
  }

  /**
   * Tries to compile the given script file.
   *
   * @param file The javascript bestia script file.
   * @return A compiled version of the script or null if there was an error.
   */
  public CompiledScript compileScript(File file) {
    LOG.trace("Compiling script file: {}.", file);

    try (Reader scriptReader = new FileReader(file)) {

      CompiledScript script = ((Compilable) engine).compile(scriptReader);
      script.eval();
      return script;

    } catch (ScriptException | IOException e) {
      LOG.error("Could not compile script.", e);
      return null;
    }
  }
}
