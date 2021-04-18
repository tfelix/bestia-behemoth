package net.bestia.zoneserver.script.item

import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.exec.ItemScriptExec

interface ItemScript {
  fun useItem(api: BestiaApi, ctx: ItemScriptExec)
}
