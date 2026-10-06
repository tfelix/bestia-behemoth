package net.bestia.zone.architecture

import com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import net.bestia.zone.BestiaException
import net.bestia.zone.architecture.ZoneClasses.ROOT
import org.junit.jupiter.api.Test

/** What each part of the zone may depend on. The packages at the bottom depend on nothing above them. */
class FeatureSliceRulesTest {

  @Test
  fun `util and geometry depend on nothing else in the zone`() {
    val leaves = arrayOf("$ROOT.util..", "$ROOT.geometry..")

    classes().that().resideInAnyPackage(*leaves)
      .should().onlyDependOnClassesThat(resideOutsideOfPackage("$ROOT..").or(resideInAnyPackage(*leaves)))
      .check(ZoneClasses.main)
  }

  /** The kernel the whole zone runs on: no Spring, no messages, no feature. */
  @Test
  fun `the ecs kernel depends only on itself, util and the JDK`() {
    classes().that().resideInAPackage("$ROOT.ecs.core..")
      .should().onlyDependOnClassesThat(
        resideInAnyPackage("$ROOT.ecs.core..", "$ROOT.util..", "java..", "kotlin..", "org.jetbrains.annotations..",
          "io.github.oshai.kotlinlogging..")
          .or(equivalentTo(BestiaException::class.java))
      )
      .check(ZoneClasses.main)
  }

  @Test
  fun `the configs depend only on the ecs kernel and Spring`() {
    classes().that().resideInAPackage("$ROOT.config..")
      .should().onlyDependOnClassesThat(
        resideInAnyPackage("$ROOT.config..", "$ROOT.ecs.core..", "java..", "kotlin..", "org.jetbrains.annotations..",
          "org.springframework..")
      )
      .check(ZoneClasses.main)
  }
}
