package net.bestia.zoneserver.script

import net.bestia.zoneserver.script.api.BestiaApi

interface Script {
  fun execute(api: BestiaApi, exec: ScriptExec)
}