package net.bestia.zoneserver.script

import org.springframework.core.io.Resource
import java.io.File
import javax.script.CompiledScript

interface ScriptCompiler {
  fun compile(fileResource: Resource): CompiledScript
}