package net.bestia.zoneserver.equip

import net.bestia.zoneserver.entity.Entity
import net.bestia.model.domain.Item
import org.springframework.stereotype.Service

@Service
class EquipService {


  /**
   * Checks if all prerequisites are fulfilled in order to equip this item to
   * the entity. If another item already occupied this slot the method wont
   * return false. Upon equipping the other item will rather get removed from
   * this equipment slot.
   *
   * @param item
   * The item to check if it can be equipped.
   * @return TRUE if the item can currently be equipped. FALSE otherwise.
   */
  fun canEquip(entity: Entity, item: Item): Boolean {

    return false
  }
}
