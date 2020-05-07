package net.bestia.zoneserver.script

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.bestia.BasicDefense
import net.bestia.model.bestia.BasicStatusValues
import net.bestia.model.entity.BasicStatusBasedValues
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.commands.AddComponentCommand
import net.bestia.zoneserver.actor.entity.component.SetIntervalCommand
import net.bestia.zoneserver.actor.entity.component.SetPositionToAbsolute
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.zoneserver.script.api.NewEntityCommand
import net.bestia.zoneserver.script.exec.AttackScriptExec
import net.bestia.zoneserver.script.exec.ScriptCallbackExec
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AttackScriptTest : BaseScriptTest() {
  private val userEntity = Entity(id = 100)
  private val emberBuilder = AttackScriptExec.Builder(
      AttackFixture.EMBER,
      userEntity
  )

  @BeforeAll
  fun setup() {
    whenever(entityRequestService.requestEntity(any())).thenReturn(Entity(10).apply {
      addComponent(PositionComponent(entityId = 10, shape = Vec3(5, 6, 7)))
      val statusValues = BasicStatusValues()
      val level = 5
      addComponent(StatusComponent(
          entityId = 10,
          statusValues = statusValues,
          statusBasedValues = BasicStatusBasedValues(
              level = level,
              statusValues = statusValues
          ),
          defense = BasicDefense()
      ))
    })
  }

  @Test
  fun `basic attack script api calls work`() {
    val exec = emberBuilder.apply {
      targetPoint = Vec3(10, 10, 0)
    }.build()

    scriptService.execute(exec)

    val commandClasses = interceptor.lastIssuedCommands.map { it.javaClass }.toSet()
    Assertions.assertEquals(
        setOf(
            SetPositionToAbsolute::class.java,
            SetIntervalCommand::class.java,
            NewEntityCommand::class.java,
            AddComponentCommand::class.java
        ),
        commandClasses
    )

    val setIntervalCommand = interceptor.lastIssuedCommands.first { it.javaClass == SetIntervalCommand::class.java } as SetIntervalCommand
    val scriptCallbackExec = ScriptCallbackExec.Builder(
        scriptCallFunction = setIntervalCommand.callbackFn,
        uuid = setIntervalCommand.uuid
    ).build()

    scriptService.execute(scriptCallbackExec)
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