package net.bestia.zone.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption

/** The zone's main and test classes, each imported once for every architecture rule. */
object ZoneClasses {
  const val ROOT = "net.bestia.zone"

  val main: JavaClasses by lazy {
    ClassFileImporter().withImportOption(ImportOption.DoNotIncludeTests()).importPackages(ROOT)
  }

  val tests: JavaClasses by lazy {
    ClassFileImporter().withImportOption(ImportOption.OnlyIncludeTests()).importPackages(ROOT)
  }
}
