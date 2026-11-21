package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.StorageOpenedSMSG
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.SlotStorage
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.master.ecs.MasterVisual
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.Steward
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.awt.Color
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuildStoragesTest {

  private val world = testWorld()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val held = listOf(
    Stronghold(RANK, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)
      .withProgress(StrongholdTrack.STORAGE, TrackProgress(2, 0)),
    Stronghold(OTHER_RANK, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)
      .withProgress(StrongholdTrack.STORAGE, TrackProgress(1, 3)),
  )

  private val storages = GuildStorages(
    repository = mockk(),
    itemStats = ItemStatCatalog(),
    strongholds = mockk {
      every { on(any()) } answers { held.firstOrNull { it.rank == firstArg<Int>() } }
      every { heldBy(GUILD) } returns held
    },
    messages = messages,
    config = StrongholdConfig(storageSlotsPerSkillLevel = 100, storageSlotsPerStorageLevel = 50),
  )

  private val steward = world.createEntity { id ->
    add(id, Position(10, 10, 0))
    add(id, Steward(RANK))
  }

  @Test
  fun `a member opens the storage with the room the skill and every Storage track give, and their rank's rights`() {
    val member = memberAt(11, accountId = 7L, masterId = 5L, take = false)

    assertNull(storages.open(world, member, steward))

    val storage = world.get(steward, SlotStorage::class)!!
    assertEquals(ItemHolder.GuildStorage(GUILD), storage.holder)
    assertEquals(2 * 100 + (2 + 1) * 50, storage.slots)
    assertEquals(mapOf(5L to SlotStorage.Use(mayPut = true, mayTake = false)), storage.users)
    assertEquals(setOf(7L), storage.viewers)
    verify { messages.sendToPlayer(7L, StorageOpenedSMSG(steward, 350, StorageOpenedSMSG.Kind.GUILD_STORAGE)) }
  }

  @Test
  fun `the storage opens only for the steward's guild, once learned, for a rank that may use it, close by`() {
    assertEquals(OpError.GUILD_STORAGE_NOT_YOUR_GUILDS, storages.open(world, memberAt(11, guild = OTHER_GUILD), steward))
    assertEquals(OpError.GUILD_STORAGE_NOT_LEARNED, storages.open(world, memberAt(11, skillLevel = 0), steward))
    assertEquals(OpError.GUILD_STORAGE_NOT_ALLOWED, storages.open(world, memberAt(11, deposit = false, take = false), steward))
    assertEquals(OpError.GUILD_STORAGE_OUT_OF_REACH, storages.open(world, memberAt(30), steward))
  }

  @Test
  fun `one member at a time, and the next may open it once the first walked away`() {
    val first = memberAt(11, accountId = 7L, masterId = 5L, name = "Mira")
    val second = memberAt(9, accountId = 8L, masterId = 6L)
    storages.open(world, first, steward)

    assertNull(storages.open(world, second, steward))
    verify { messages.sendToPlayer(8L, OperationErrorSMSG(OpError.GUILD_STORAGE_IN_USE, listOf("Mira"))) }
    assertEquals(setOf(5L), world.get(steward, SlotStorage::class)!!.users.keys)

    world.get(first, Position::class)!!.x = 40
    storages.closeLeft(world)
    assertTrue(world.get(steward, SlotStorage::class)!!.users.isEmpty())

    assertNull(storages.open(world, second, steward))
    assertEquals(setOf(6L), world.get(steward, SlotStorage::class)!!.users.keys)
  }

  private fun memberAt(
    x: Long,
    guild: Long = GUILD,
    accountId: Long = 7L,
    masterId: Long = 5L,
    skillLevel: Int = 2,
    deposit: Boolean = true,
    take: Boolean = true,
    name: String = "Aldric",
  ): EntityId {
    val skills = if (skillLevel > 0) mapOf(SkillId.GUILD_STORAGE.name to skillLevel) else emptyMap()

    return world.createEntity { id ->
      add(id, Position(x, 10, 0))
      add(id, Master(masterId))
      add(id, Account(accountId))
      add(id, MasterVisual(1, name, Color.WHITE, Color.BLACK, Face.entries.first(), BodyType.entries.first(), Hairstyle.entries.first()))
      add(id, GuildMembership(guild, taxPercent = 5, mayDeposit = deposit, mayTake = take, skillLevels = skills))
    }
  }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
    const val RANK = 1
    const val OTHER_RANK = 2
  }
}
