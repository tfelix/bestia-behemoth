package net.bestia.zone.architecture

import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
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
}
