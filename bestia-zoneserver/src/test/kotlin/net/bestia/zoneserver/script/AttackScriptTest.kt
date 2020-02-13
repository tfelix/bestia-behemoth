package net.bestia.zoneserver.script

import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.api.SetPositionToCommand
import net.bestia.zoneserver.script.exec.AttackScriptExec
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AttackScriptTest : BaseScriptTest() {
  private val userEntity = Entity(id = 100)
  private val emberBuilder = AttackScriptExec.Builder(
      AttackFixture.EMBER,
      userEntity
  )

  @Test
  fun `basic attack script api calls work`() {
    val exec = emberBuilder.apply {
      targetPoint = Vec3(10, 10, 0)
    }.build()

    scriptService.execute(exec)

    val commandClasses = interceptor.lastIssuedCommands.map { it.javaClass }.toSet()
    Assertions.assertEquals(
        setOf(SetPositionToCommand::class.java),
        commandClasses
    )
  }

  @Test
  fun `call into function`() {
    val exec = emberBuilder.apply {
      callFunction = "onTick"
      targetPoint = Vec3(10, 10, 0)
    }.build()

    scriptService.execute(exec)
  }
}