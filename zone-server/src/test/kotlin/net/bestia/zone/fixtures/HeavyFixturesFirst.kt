package net.bestia.zone.world

import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.ClassOrdererContext

/** Runs every [ReadsLargeWorlds] class first, so each large world is built once and then freed. Others keep their order. */
class LargeWorldsFirst : ClassOrderer {

  override fun orderClasses(context: ClassOrdererContext) {
    context.classDescriptors.sortBy { descriptor ->
      if (descriptor.isAnnotated(ReadsLargeWorlds::class.java)) 0 else 1
    }
  }
}
