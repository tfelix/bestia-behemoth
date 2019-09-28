package net.bestia.zoneserver.script

import java.io.File
import javax.script.CompiledScript

interface ScriptCompiler {
  fun compile(file: File): CompiledScript
}