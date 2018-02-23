package net.bestia.zoneserver.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.script.CompiledScript;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The script cache will accept folders which contain java scripts. It will
 * start to compile the folder content and save the compiled script for later
 * use. The scripts can be reused but keep in mind that the scripts are usually
 * not thread safe and should store no internal persistent state.
 *
 * @author Thomas Felix
 */
@Component
public class ScriptCache {

  private static final Logger LOG = LoggerFactory.getLogger(ScriptCache.class);

  private final Map<String, CompiledScript> cache = new HashMap<>();

  private final ScriptFileResolver resolver;
  private final ScriptCompiler compiler;

  public ScriptCache(ScriptCompiler compiler, ScriptFileResolver resolver) {

    this.resolver = Objects.requireNonNull(resolver);
    this.compiler = Objects.requireNonNull(compiler);
  }

  private void setupScript(File scriptFile, String key) {
    final CompiledScript compiledScript = compiler.compileScript(scriptFile);
    cache.put(key, compiledScript);
  }

  /**
   * Adds a folder to the script cache. It will immediately start to compile
   * all the scripts inside this folder.
   *
   * @param scriptBasePath The folder to add to the cache.
   */
  public void cacheFolder(Path scriptBasePath) {

    LOG.info("Adding folder {} of scripts {} to script cache.", scriptBasePath);

    // Starting to compile the scripts.
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(scriptBasePath)) {
      for (Path scriptPath : directoryStream) {
        LOG.debug("Compiling script: {}", scriptPath);

        final File scriptFile = resolver.getScriptFile(scriptPath.toString());
        final String scriptKey = getRelativePath(scriptBasePath, scriptPath);

        setupScript(scriptFile, scriptKey);
      }
    } catch (IOException e) {
      LOG.error("Could not compile script.", e);
    }
  }

  private String getRelativePath(Path basePath, Path scriptPath) {
    return scriptPath.relativize(basePath).toString();
  }

  /**
   * Returns the compiled script of the given type and name.
   *
   * @param name The name of the scriptfile (without extention).
   * @return The compiled script or null of no script was found.
   */
  public CompiledScript getScript(String name) {
    Objects.requireNonNull(name);
    LOG.trace("Requesting script file from cache: {}.", name);

    if (!cache.containsKey(name)) {
      LOG.trace("Script was not found in cache. Compiling it first.");

      final File scriptFile = resolver.getScriptFile(name);
      setupScript(scriptFile, name);
    }

    return cache.get(name);
  }
}