import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.api.GradleException
import java.io.File

/**
 * Reads `zone-server`'s `worldgen:` block and hands it to the offline worldgen tools as flags.
 *
 * This is what makes `./gradlew :worldgen:viewer -Pgenesis` show the world the server actually boots rather
 * than one that resembles it. The alternative - a `genesisConfig()` constant in `worldgen` mirroring the
 * numbers in `application.yml` - is two copies of a world definition, and the copy nobody runs is the one that
 * goes stale. Here there is one definition and the viewer reads it.
 *
 * ### Why this lives in the build rather than in the tool
 *
 * `worldgen` depends on the Kotlin stdlib and the JDK and nothing else, deliberately - it is linked into the
 * server today and possibly the client later. Teaching `ViewerMain` to parse YAML would put a parser inside
 * that boundary for the benefit of one debug tool. The build already has Jackson, already knows where both
 * projects are on disk, and is the layer whose job is wiring, so the translation belongs here and `worldgen`
 * keeps taking plain flags.
 *
 * @see net.bestia.worldgen.viewer.WorldArgs the flags on the other side of this
 */
object WorldGenSettings {

  /**
   * Birth settings that decide terrain, and the flag that expresses each.
   *
   * Keys are `WorldGenConfig`'s relaxed-binding names as they appear in `application.yml`; values are
   * `WorldArgs` flags. Both sides of every pair have to exist or the mapping is a lie.
   */
  private val FLAGS = mapOf(
    "seed" to "--seed",
    "width-cells" to "--width-cells",
    "height-cells" to "--height-cells",
    "cell-size-metres" to "--cell-size",
    "chunk-size" to "--chunk-size",
    "chunk-height" to "--chunk-height",
    "voxel-size-metres" to "--voxel-size",
    "sea-level-metres" to "--sea-level",
    "wrap-x" to "--wrap-x",
    "wrap-y" to "--wrap-y"
  )

  /**
   * Settings that say nothing about what the terrain *is*.
   *
   * Listed rather than skipped by default, because the check below turns an unrecognised key into a build
   * failure. A birth setting added to `WorldGenConfig` and not to [FLAGS] would otherwise be silently dropped,
   * and the viewer would go back to showing a world that merely resembles the server's - which is the exact
   * failure this object exists to remove.
   */
  private val IGNORED = setOf("name", "on-mismatch")

  private val MAPPER = ObjectMapper(YAMLFactory())

  /**
   * The `worldgen` block of [applicationYml] as command-line flags for `ViewerMain` or `ProbeMain`.
   *
   * Only keys actually present are forwarded, so anything left to its default in the configuration is left to
   * its default in the tool - which is the same value, since both sides read `WorldGenConfig`'s.
   *
   * `@JvmStatic` because the caller is a Groovy build script, which cannot see a Kotlin `object`'s members as
   * statics without it.
   */
  @JvmStatic
  fun toolArgs(applicationYml: File): List<String> {
    if (!applicationYml.isFile) {
      throw GradleException("No world settings at ${applicationYml.absolutePath}")
    }

    val root = MAPPER.readValue(applicationYml, Map::class.java)
    val block = root["worldgen"] as? Map<*, *>
      ?: throw GradleException(
        "${applicationYml.name} has no 'worldgen:' block, so there are no birth settings to mirror"
      )

    val settings = block.entries.associate { (key, value) -> key.toString() to value }

    val unforwardable = settings.keys - FLAGS.keys - IGNORED
    if (unforwardable.isNotEmpty()) {
      throw GradleException(
        "worldgen setting(s) ${unforwardable.joinToString(", ")} in ${applicationYml.name} cannot be " +
            "forwarded to the offline tools. If they decide terrain, the viewer would show a different world " +
            "than the server boots: add each to FLAGS in buildSrc/src/main/kotlin/WorldGenSettings.kt and to " +
            "WorldArgs in worldgen. If they do not, add them to IGNORED there."
      )
    }

    // A world with no pinned seed is a different world on every boot, so there is no "the server's world" to
    // open. Failing here beats opening an arbitrary one that looks like an answer.
    if (settings["seed"] == null) {
      throw GradleException(
        "worldgen.seed is not set in ${applicationYml.name}, so the server draws a new seed on every boot " +
            "and there is no single world to show. Pin it there, or pass -Pseed=<n> to choose one here."
      )
    }

    return FLAGS.entries.flatMap { (key, flag) ->
      settings[key]?.let { listOf(flag, it.toString()) } ?: emptyList()
    }
  }
}
