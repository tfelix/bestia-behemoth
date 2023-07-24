package net.bestia.zoneserver.script.api

import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import net.bestia.zoneserver.script.AttackEntityScriptExec
import net.bestia.zoneserver.script.AttackFixture
import net.bestia.zoneserver.script.ScriptExec
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BestiaApiTest {

    private lateinit var sut: BestiaApi

    @MockK
    private lateinit var idGeneratorService: IdGenerator

    @MockK
    private lateinit var mobFactory: MobFactory

    @MockK
    private lateinit var entityCollisionService: EntityCollisionService

    @MockK
    private lateinit var entityRequestService: EntityRequestService

    private val scriptExec = AttackEntityScriptExec(
        attackerEntityId = 1,
        callbackContext = null,
        attack = AttackFixture.EMBER,
        targetEntityId = 2
    )

    @BeforeEach
    fun setup() {
        sut = BestiaApi("test", idGeneratorService, mobFactory, entityCollisionService, entityRequestService)
    }

    @Test
    fun test() {
        sut.findEntities2(Vec3.ZERO, ::callbackMethod)

        val command = sut.messages[0]
        println(command)
    }

    private fun callbackMethod(api: BestiaApi, exec: ScriptExec, entityIds: Set<Long>) {
        // no op
    }
}