package net.bestia.zone.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAnnotation
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import net.bestia.zone.SpringContextLock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.boot.test.context.SpringBootTest

/** Test classes run in parallel; one that shares the Spring context without its lock races every scenario. */
@ReadsZoneClasses
class SpringTestRulesTest {

  private val contextLock = describe<JavaAnnotation<*>>("@ResourceLock(SpringContextLock.NAME)") { annotation ->
    annotation.rawType.isEquivalentTo(ResourceLock::class.java) &&
      annotation.get("value").orElse(null) == SpringContextLock.NAME
  }

  @Test
  fun `every Spring test holds the shared context lock`() {
    classes().that().areAnnotatedWith(SpringBootTest::class.java)
      .should().beAnnotatedWith(contextLock)
      .check(ZoneClasses.tests)
  }
}
