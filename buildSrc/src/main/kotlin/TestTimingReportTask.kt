import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reports where the test suite spends its time, so that "make the tests faster" can start from
 * numbers instead of from a guess.
 *
 * It reads the JUnit XML that Gradle already writes under `<module>/build/test-results/test/`
 * rather than hooking a listener into the test JVM. That has two consequences worth keeping: the
 * report costs the suite nothing while it runs, and it can be produced after the fact - from the
 * run that just happened, from a colleague's run, or from a CI artifact - without re-running
 * anything. The flip side is that it reports on whatever is currently on disk, so a stale or
 * partial directory is reported as such instead of being silently averaged in.
 *
 * Two different "times" come out of that XML and the report keeps them apart, because the gap
 * between them is itself a finding:
 *
 *   - the sum of `<testcase time>`, which is what the test methods cost, and
 *   - `<testsuite time>`, which is what the class cost end to end.
 *
 * The difference is per-class fixture work - `@BeforeAll`, a Spring context boot, a generated
 * world - that JUnit charges to no individual test. A class with a large gap is not slow because
 * its assertions are slow, and speeding up the test bodies there buys nothing.
 */
@UntrackedTask(because = "Reports on build outputs that other tasks produce; has no up-to-date state of its own")
abstract class TestTimingReportTask : DefaultTask() {

  /**
   * Repository root. Every `<module>/build/test-results/test/` below it is picked up.
   *
   * Deliberately not an `@InputDirectory`: declaring the repository root as a tracked input makes
   * Gradle fingerprint the entire tree - sources, build outputs, the lot - before a task that only
   * ever reads a few hundred small XML files.
   */
  @get:Internal
  abstract val resultsRoot: DirectoryProperty

  /** How many rows each "slowest" table shows. */
  @get:Internal
  abstract val timingRows: Property<Int>

  /** Per-test rows, for diffing one run against the next. */
  @get:Internal
  abstract val csv: RegularFileProperty

  private data class Case(
    val module: String,
    val className: String,
    val name: String,
    val seconds: Double,
    val skipped: Boolean,
    /** Position in the class, which is the order the XML was written in - i.e. execution order. */
    val position: Int
  )

  private data class Suite(
    val module: String,
    val className: String,
    /** End-to-end cost of the class, fixtures included. */
    val seconds: Double,
    val cases: List<Case>
  ) {
    val caseSeconds get() = cases.sumOf { it.seconds }

    /** Setup/teardown the class paid for outside any test method. Never negative in practice. */
    val fixtureSeconds get() = (seconds - caseSeconds).coerceAtLeast(0.0)

    val simpleName get() = className.substringAfterLast('.')
  }

  @TaskAction
  fun report() {
    val suites = readSuites()
    if (suites.isEmpty()) {
      logger.lifecycle("No test results found. Run the test suite first, e.g. ./gradlew test")
      return
    }

    val cases = suites.flatMap { it.cases }
    val total = suites.sumOf { it.seconds }

    printModules(suites, total)
    printSlowestClasses(suites, total)
    printSlowestTests(cases, total)
    printFixtureHeavyClasses(suites)
    printDistribution(cases, total)
    printConcentration(cases, total)

    csv.orNull?.asFile?.let { writeCsv(it, cases) }
  }

  private fun readSuites(): List<Suite> {
    val root = resultsRoot.get().asFile
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    return root.listFiles { f: File -> f.isDirectory }.orEmpty()
      .flatMap { module ->
        val dir = File(module, "build/test-results/test")
        val xmls = dir.listFiles { f: File -> f.name.endsWith(".xml") }.orEmpty()
        xmls.map { xml ->
          val suite = builder.parse(xml).documentElement
          val className = suite.getAttribute("name")
          val cases = suite.getElementsByTagName("testcase").let { nodes ->
            (0 until nodes.length).map { i ->
              val case = nodes.item(i) as Element
              Case(
                module = module.name,
                className = className,
                name = case.getAttribute("name"),
                seconds = case.getAttribute("time").toDoubleOrNull() ?: 0.0,
                skipped = case.getElementsByTagName("skipped").length > 0,
                position = i
              )
            }
          }
          Suite(module.name, className, suite.getAttribute("time").toDoubleOrNull() ?: 0.0, cases)
        }
      }
  }

  private fun printModules(suites: List<Suite>, total: Double) {
    header("BY MODULE")
    logger.lifecycle(row("%-16s %10s %10s %8s %7s %7s", "module", "total", "fixtures", "classes", "tests", "share"))
    suites.groupBy { it.module }
      .entries.sortedByDescending { (_, s) -> s.sumOf { it.seconds } }
      .forEach { (module, moduleSuites) ->
        val moduleTotal = moduleSuites.sumOf { it.seconds }
        logger.lifecycle(
          row("%-16s %10s %10s %8d %7d %6.1f%%",
            module,
            secs(moduleTotal),
            secs(moduleSuites.sumOf { it.fixtureSeconds }),
            moduleSuites.size,
            moduleSuites.sumOf { it.cases.count { c -> !c.skipped } },
            100 * moduleTotal / total
          )
        )
      }
    val skipped = suites.sumOf { s -> s.cases.count { it.skipped } }
    logger.lifecycle(
      row("%-16s %10s %10s %8d %7d",
        "TOTAL", secs(total), secs(suites.sumOf { it.fixtureSeconds }), suites.size,
        suites.sumOf { s -> s.cases.count { !it.skipped } }
      ) + if (skipped > 0) "  ($skipped skipped)" else ""
    )
  }

  private fun printSlowestClasses(suites: List<Suite>, total: Double) {
    header("SLOWEST CLASSES")
    suites.sortedByDescending { it.seconds }.take(timingRows.get()).forEach {
      logger.lifecycle(
        row("%9s %6.1f%%  %3d tests, %8s in fixtures  %s/%s",
          secs(it.seconds), 100 * it.seconds / total, it.cases.size, secs(it.fixtureSeconds),
          it.module, it.simpleName
        )
      )
    }
  }

  private fun printSlowestTests(cases: List<Case>, total: Double) {
    header("SLOWEST TESTS")
    cases.sortedByDescending { it.seconds }.take(timingRows.get()).forEach {
      logger.lifecycle(
        row("%9s %6.1f%%  %s/%s.%s",
          secs(it.seconds), 100 * it.seconds / total, it.module, it.simpleName(), it.name
        )
      )
    }
  }

  /**
   * Classes whose cost is mostly not in their test methods. These are the ones where the lever is
   * the fixture - share a context, generate a smaller world, move the setup to `@BeforeAll` - and
   * where tuning the assertions is wasted effort.
   */
  private fun printFixtureHeavyClasses(suites: List<Suite>) {
    header("FIXTURE-HEAVY CLASSES")
    val heavy = suites
      .filter { it.seconds >= FIXTURE_FLOOR_SECONDS && it.fixtureSeconds > it.caseSeconds }
      .sortedByDescending { it.fixtureSeconds }
      .take(timingRows.get())

    if (heavy.isEmpty()) {
      logger.lifecycle("  none: every slow class spends its time inside test methods")
      return
    }
    heavy.forEach {
      logger.lifecycle(
        row("%9s of %8s (%3.0f%%) outside test methods  %s/%s",
          secs(it.fixtureSeconds), secs(it.seconds), 100 * it.fixtureSeconds / it.seconds,
          it.module, it.simpleName
        )
      )
    }

    // The same cost, charged the other way: JUnit bills a lazily built fixture to whichever test
    // triggered it, so a single test carrying its whole class usually means shared setup, not a
    // slow test. Worth separating, because the two have the same fix and look completely different.
    val frontLoaded = suites
      .filter { it.seconds >= FIXTURE_FLOOR_SECONDS }
      .mapNotNull { suite ->
        val real = suite.cases.filter { !it.skipped }
        if (real.size < 3) return@mapNotNull null
        val peak = real.maxBy { it.seconds }
        if (peak.seconds / suite.seconds > FRONT_LOADED_SHARE) suite to peak else null
      }
      .sortedByDescending { (_, peak) -> peak.seconds }
      .take(timingRows.get())

    if (frontLoaded.isNotEmpty()) {
      logger.lifecycle("")
      logger.lifecycle("  ...and classes where one test carries the rest (fixture billed to the first caller):")
      frontLoaded.forEach { (suite, peak) ->
        logger.lifecycle(
          row("  %9s of %8s (%3.0f%%) in test #%d of %d  %s/%s.%s",
            secs(peak.seconds), secs(suite.seconds), 100 * peak.seconds / suite.seconds,
            peak.position + 1, suite.cases.size, suite.module, suite.simpleName, peak.name
          )
        )
      }
    }
  }

  private fun printDistribution(cases: List<Case>, total: Double) {
    header("DISTRIBUTION")
    val upperBounds = listOf(
      0.01 to "<10ms", 0.1 to "<100ms", 1.0 to "<1s", 5.0 to "<5s", 30.0 to "<30s", Double.MAX_VALUE to ">=30s"
    )
    var lower = 0.0
    upperBounds.forEach { (upper, label) ->
      val bucket = cases.filter { it.seconds >= lower && it.seconds < upper }
      val time = bucket.sumOf { it.seconds }
      logger.lifecycle(row("  %-8s %5d tests %10s %6.1f%%", label, bucket.size, secs(time), 100 * time / total))
      lower = upper
    }
  }

  /**
   * How lopsided the suite is. When a handful of tests own half the clock, a targeted fix beats
   * anything applied across the board - and when they do not, only parallelism or fewer tests will
   * move the number.
   */
  private fun printConcentration(cases: List<Case>, total: Double) {
    header("CONCENTRATION")
    val caseTotal = cases.sumOf { it.seconds }
    val sorted = cases.sortedByDescending { it.seconds }
    var cumulative = 0.0
    var next = 0
    val marks = listOf(0.5, 0.8, 0.95)
    sorted.forEachIndexed { index, case ->
      cumulative += case.seconds
      while (next < marks.size && cumulative >= marks[next] * caseTotal) {
        logger.lifecycle(
          row("  %3.0f%% of test-method time is in the slowest %d tests (%.1f%% of %d)",
            marks[next] * 100, index + 1, 100.0 * (index + 1) / sorted.size, sorted.size
          )
        )
        next++
      }
    }
  }

  private fun writeCsv(file: File, cases: List<Case>) {
    file.parentFile.mkdirs()
    file.bufferedWriter().use { out ->
      out.appendLine("module,class,test,seconds,skipped")
      cases.sortedByDescending { it.seconds }.forEach {
        out.appendLine("""${it.module},${it.className},"${it.name.replace("\"", "\"\"")}",${it.seconds},${it.skipped}""")
      }
    }
    logger.lifecycle("")
    logger.lifecycle("Per-test rows written to ${file.relativeTo(resultsRoot.get().asFile)}")
  }

  private fun Case.simpleName() = className.substringAfterLast('.')

  /** Always [Locale.ROOT]: a default-locale "0,11s" is unparseable and reads wrong outside one region. */
  private fun row(format: String, vararg args: Any?) = String.format(Locale.ROOT, format, *args)

  private fun header(title: String) {
    logger.lifecycle("")
    logger.lifecycle("--- $title ---")
  }

  private fun secs(value: Double) = if (value >= 100) row("%.0fs", value) else row("%.2fs", value)

  private companion object {
    /** Below this a class is noise, whatever the ratio of fixture to test time inside it. */
    const val FIXTURE_FLOOR_SECONDS = 2.0

    /** A test owning more than this much of its class is reported as carrying shared setup. */
    const val FRONT_LOADED_SHARE = 0.5
  }
}
