package net.bestia.zoneserver.script.item

import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.ItemEntityScriptExec

class AppleItemScript : ItemScript {
  override val itemDatabaseName = "apple"

    override fun executeItemOnEntity(api: BestiaApi, exec: ItemEntityScriptExec) {
    api.findEntity(exec.userEntityId)
        .condition()
        .addHp(10)
  }
}