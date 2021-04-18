package net.bestia.zoneserver.script.item

import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.exec.ItemScriptExec

class ItemApple : ItemScript {
  override fun useItem(api: BestiaApi, ctx: ItemScriptExec) {
    api.findEntity(ctx.userEntityId)
        .condition()
        .addHp(10)
  }
}