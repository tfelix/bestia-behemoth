package net.bestia.zone.fixtures

import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext

/** Runs the classes of each [HeavyFixture] back to back, in the enum's order, before all others. Others keep their order. */
class HeavyFixturesFirst : ClassOrderer {

  override fun orderClasses(context: ClassOrdererContext) {
    context.classDescriptors.sortBy { descriptor ->
      HeavyFixture.readBy(descriptor.testClass)?.ordinal ?: HeavyFixture.entries.size
    }
  }
}
