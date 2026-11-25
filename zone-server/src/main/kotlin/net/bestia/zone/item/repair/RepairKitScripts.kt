package net.bestia.zone.item.repair

import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** One [RepairKitScript] per [RepairKit], since an item script answers for a single item. */
@Configuration
class RepairKitScripts {

  @Bean
  fun weaponRepairKitScript(
    templates: ItemTemplateRegistry,
    inventoryService: InventoryService,
    asyncJobExecutor: AsyncJobExecutor,
  ): RepairKitScript {
    return RepairKitScript(RepairKit.WEAPON, templates, inventoryService, asyncJobExecutor)
  }

  @Bean
  fun armorRepairKitScript(
    templates: ItemTemplateRegistry,
    inventoryService: InventoryService,
    asyncJobExecutor: AsyncJobExecutor,
  ): RepairKitScript {
    return RepairKitScript(RepairKit.ARMOR, templates, inventoryService, asyncJobExecutor)
  }
}
