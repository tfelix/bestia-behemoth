package net.bestia.zoneserver.script

class WrongScriptTypeException(
    exec: ScriptExec,
    expectedClass: Class<out ScriptExec>
) : BestiaScriptException("Script exec $exec does not match expected ${expectedClass.simpleName}")