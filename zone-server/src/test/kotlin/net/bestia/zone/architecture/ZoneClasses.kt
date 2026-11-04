package net.bestia.zone.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

/**
 * The zone's main and test classes, imported once for the [ReadsZoneClasses] group. Each import holds a few
 * hundred megabytes, so [release] lets them go when the group is done.
 */
object ZoneClasses {
  const val ROOT = "net.bestia.zone"

  private var mainClasses: JavaClasses? = null
  private var testClasses: JavaClasses? = null

  val main: JavaClasses
    @Synchronized get() {
      return mainClasses ?: importZone(ImportOption.DoNotIncludeTests()).also { mainClasses = it }
    }

  val tests: JavaClasses
    @Synchronized get() {
      return testClasses ?: importZone(ImportOption.OnlyIncludeTests()).also { testClasses = it }
    }

  @Synchronized
  fun release() {
    mainClasses = null
    testClasses = null
  }

  private fun importZone(option: ImportOption): JavaClasses {
    return ClassFileImporter().withImportOption(option).importPackages(ROOT)
  }
}
