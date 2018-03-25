package net.bestia.zoneserver.script;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.zoneserver.script.env.SimpleScriptEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.script.CompiledScript;
import java.util.Objects;

/**
 * This class is responsible for fetching the script, creating a appropriate
 * script binding context and then executing the called script.
 * <p>
 * It also provides the script API so the scripts can interact with the bestia
 * service.
 *
 * @author Thomas Felix
 */
@Service
public class ScriptService {

  private static final Logger LOG = LoggerFactory.getLogger(ScriptService.class);

  private final EntityService entityService;
  private final ScriptCache scriptCache;
  private final ScriptExecutionService scriptExecutionService;

  @Autowired
  public ScriptService(
          EntityService entityService,
          ScriptExecutionService scriptExecutionService,
          ScriptCache cache) {

    this.entityService = Objects.requireNonNull(entityService);
    this.scriptCache = Objects.requireNonNull(cache);
    this.scriptExecutionService = Objects.requireNonNull(scriptExecutionService);

  }

  private CompiledScript resolveScript(ScriptAnchor scriptAnchor) {
    final CompiledScript script = scriptCache.getScript(scriptAnchor.getName());

    if (script == null) {
      LOG.warn("Did not find script file: {} ({})", scriptAnchor);
      throw new IllegalArgumentException("Could not find script.");
    }

    return script;
  }

  /**
   * Central entry point for calling a script execution from the Bestia
   * system. This will fetch the script from cache, if cache does not hold the
   * script it will attempt to compile it. It will then set the script
   * environment and execute its main function.
   *
   * @param name The name of the script to be called.
   */
  public void callScriptMainFunction(String name) {
    Objects.requireNonNull(name);
    LOG.debug("Calling script: {}.", name);

    final ScriptAnchor ident = ScriptAnchor.fromString(name);
    final CompiledScript script = resolveScript(ident);

    final SimpleScriptEnv scriptEnv = new SimpleScriptEnv();

    scriptExecutionService.execute(ident.getFunctionName(), script, scriptEnv);
  }

  /**
   * The script callback is triggered via a counter which was initially set
   * into the {@link ScriptComponent}.
   *
   * @param scriptUuid     The uuid of the script (an entity can have more then one
   *                       callback script attached).
   * @param scriptEntityId The script entity whose callback is about to be triggered.
   */
  public void callScriptIntervalCallback(long scriptEntityId, String scriptUuid) {

    LOG.trace("Script {} interval called.", scriptEntityId);

    final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    final String callbackAnchorString = scriptComp.getCallback(scriptUuid).getScript();
    final ScriptAnchor anchor = ScriptAnchor.Companion.fromString(callbackAnchorString);
    final CompiledScript script = resolveScript(anchor);

    scriptExecutionService.execute(anchor.getFunctionName(), script, null);
  }
}
