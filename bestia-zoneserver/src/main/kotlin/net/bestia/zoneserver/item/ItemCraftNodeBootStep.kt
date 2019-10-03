package net.bestia.zoneserver.item

import net.bestia.zoneserver.actor.bootstrap.NodeBootStep
import org.springframework.stereotype.Component

@Component
class ItemCraftNodeBootStep(
    private val resolverService: ResourceMatrixResolverService
) : NodeBootStep {
  override val bootStepName = "Generate Item craft LSH hashes"

  override fun execute() {
    resolverService.hashAllItems()
  }
}