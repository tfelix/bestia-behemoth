package net.bestia.zone.architecture

import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.world.WorldProvisioningTest
import org.junit.jupiter.api.Test

/**
 * Tests read generated worlds from `TestWorlds`, which builds each one once and lets it go when memory runs
 * short. A test that builds its own, or pins one in a static field, brings back the full heap these rules exist
 * to prevent.
 */
class TestWorldRulesTest {

  /** Classes whose claim is that two builds agree; a cached world would compare a world with itself. */
  private val buildsTwiceOnPurpose = arrayOf(WorldProvisioningTest::class.java)

  private val buildsAWorld = describe<JavaMethodCall>("StandardWorld.build") { call ->
    call.targetOwner.isEquivalentTo(StandardWorld::class.java) && call.name.startsWith("build")
  }

  @Test
  fun `tests ask TestWorlds for a world instead of building one`() {
    noClasses().that().doNotBelongToAnyOf(*buildsTwiceOnPurpose)
      .should().callMethodWhere(buildsAWorld)
      .check(ZoneClasses.tests)
  }

  @Test
  fun `no test holds a generated world in a static field`() {
    fields().that().haveRawType(GeneratedWorld::class.java)
      .should().notBeStatic()
      .allowEmptyShould(true)
      .check(ZoneClasses.tests)
  }
}
