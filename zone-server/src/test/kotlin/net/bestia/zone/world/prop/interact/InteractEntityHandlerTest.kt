package net.bestia.zone.world.prop.interact

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.dialog.conversation.TalkService
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.construction.Building
import net.bestia.zone.ecs.construction.ConstructionSite
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PlayerStructureIdentity
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.StaticEntityKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a click means is decided by what was clicked, so these are the branches rather than the message.
 */
class InteractEntityHandlerTest {

  private val accountId = 1L
  private val standingAt = Vec3L(50, 50, 32)

  private lateinit var world: World
  private lateinit var messages: OutMessageProcessor
  private lateinit var dialogs: DialogService
  private lateinit var talk: TalkService
  private lateinit var handler: InteractEntityHandler
  private var actor: EntityId = 0L

  @BeforeEach
  fun setUp() {
    world = testWorld()
    messages = mockk(relaxed = true)
    dialogs = mockk(relaxed = true)
    talk = mockk(relaxed = true)

    actor = world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Account(accountId))
    }

    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(accountId, masterId = 1L, masterEntityId = actor)

    handler = InteractEntityHandler(
      connectionInfoService = connectionInfoService,
      deadActionGuard = DeadActionGuard(world),
      dialogService = dialogs,
      talkService = talk,
      outMessageProcessor = messages,
      world = world
    )
  }

  /** `TalkService` decides whether they can hear you and what they say; all the handler owes is the call. */
  @Test
  fun `clicking a townsperson speaks to them`() {
    val villager = world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, Townsfolk(identity = 1L))
    }

    handler.handle(interactWith(villager))

    verify { talk.open(accountId, actor, villager) }
  }

  @Test
  fun `and clicking something that is nobody does not`() {
    val scenery = world.createEntity { id -> add(id, Position.fromVec3(standingAt)) }

    handler.handle(interactWith(scenery))

    verify(exactly = 0) { talk.open(any(), any(), any()) }
  }

  @Test
  fun `clicking a site in reach starts building it`() {
    val site = spawnSite(at = standingAt)

    handler.handle(interactWith(site))

    assertEquals(site, world.get(actor, Building::class)?.siteEntityId)
  }

  @Test
  fun `clicking the site being built stops`() {
    val site = spawnSite(at = standingAt)

    handler.handle(interactWith(site))
    handler.handle(interactWith(site))

    assertNull(world.get(actor, Building::class), "a second click is how you stop")
  }

  @Test
  fun `clicking a second site switches to it`() {
    val first = spawnSite(at = standingAt)
    val second = spawnSite(at = standingAt)

    handler.handle(interactWith(first))
    handler.handle(interactWith(second))

    assertEquals(second, world.get(actor, Building::class)?.siteEntityId)
  }

  @Test
  fun `a site out of reach is refused with something the player can read`() {
    val site = spawnSite(at = Vec3L(standingAt.x + 20, standingAt.y, standingAt.z))

    handler.handle(interactWith(site))

    assertNull(world.get(actor, Building::class))

    val sent = slot<SMSG>()
    verify { messages.sendToPlayer(accountId, capture<SMSG>(sent)) }
    assertEquals(OpError.BUILD_OUT_OF_RANGE, (sent.captured as OperationErrorSMSG).code)
  }

  @Test
  fun `clicking a finished structure opens its placeholder`() {
    val workbench = world.createEntity { id ->
      add(id, Position.fromVec3(standingAt))
      add(id, PlayerStructureIdentity(7L))
    }

    handler.handle(interactWith(workbench))

    verify { dialogs.send(accountId, DialogId.WORKBENCH_PLACEHOLDER, any(), workbench) }
  }

  @Test
  fun `an id that names nothing is ignored rather than answered`() {
    handler.handle(interactWith(999_999L))

    verify(exactly = 0) { messages.sendToPlayer(any(), any<SMSG>()) }
  }

  private fun spawnSite(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(
        id,
        ConstructionSite(
          kind = StaticEntityKind.WORKBENCH,
          ownerMasterId = 1L,
          structureId = 7L,
          yaw = 0f,
          totalSeconds = 20f
        )
      )
    }
  }

  private fun interactWith(targetEntityId: EntityId): InteractEntityCMSG {
    return InteractEntityCMSG(playerId = accountId, targetEntityId = targetEntityId, args = ScriptArgs.EMPTY)
  }
}
