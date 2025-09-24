package net.bestia.zone.scenarios

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterFactory
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.geometry.Vec3
import net.bestia.zone.geometry.Vec3L
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.awt.Color

@Component
@Profile("test")
class ScenarioDataSetup(
  private val accountFactory: AccountFactory,
  private val masterFactory: MasterFactory,
  private val accountRepository: AccountRepository,
  private val bestiaRepository: BestiaRepository,
  private val playerBestiaCreateOperation: PlayerBestiaCreateOperation,
  private val applicationContext: ApplicationContext
) {

  /**
   * We need to do it like this because our YML loader needs to run first to setup the mob
   * data so we can not initialize the test data inside a bean because the YML would not be loaded
   * in time.
   */
  @Component
  @Profile("test")
  @Order(Ordered.LOWEST_PRECEDENCE)
  class TestDataInitializer(
    private val scenarioDataSetup: ScenarioDataSetup
  ) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
      scenarioDataSetup.setupTestData()
    }
  }

  data class TestFixture(
    val account1: AccountData,
    val account2: AccountData,
    val account3: AccountData,
    val bestia1: Bestia
  ) {
    data class AccountData(
      val account: Account,
      val masterIds: List<Long>
    )
  }

  @Transactional
  fun setupTestData() {
    LOG.info { "Creating scenario test data..." }

    // Create test data after all runners (mob import) have completed
    val account1 = createAccount1()
    val account2 = createAccount2()
    val account3 = createAccount3()

    val testFixture = TestFixture(
      account1,
      account2,
      account3,
      bestiaRepository.findByIdOrThrow(1)
    )

    // Register TestFixture as a singleton bean for injection in tests
    if (applicationContext is GenericApplicationContext) {
      val beanFactory = applicationContext.beanFactory
      beanFactory.registerSingleton("testFixture", testFixture)
    }
  }

  fun createAccount1(): TestFixture.AccountData {
    val account1 = accountFactory.createAccount(1L)

    val createMasterData1 = MasterFactory.CreateMasterData(
      name = "account1-1",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    masterFactory.create(account1, createMasterData1)

    LOG.info { "Account 1 (ID: ${account1.id}) created" }

    val master1 = account1.master.first()

    // add 1. bestia to first master.
    playerBestiaCreateOperation.createAndSpawn(
      master1.id,
      PlayerBestiaCreateData(
        bestiaIdentifier = "blob",
        spawnPosition = Vec3L.ZERO
      )
    )

    // add 2. bestia to first master.
    playerBestiaCreateOperation.createAndSpawn(
      master1.id,
      PlayerBestiaCreateData(
        bestiaIdentifier = "blob",
        spawnPosition = Vec3L.ZERO
      )
    )

    val createMasterData2 = MasterFactory.CreateMasterData(
      name = "account1-2",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    val master2 = masterFactory.create(account1, createMasterData2)

    // add bestia to second master.
    playerBestiaCreateOperation.createAndSpawn(
      master2.id,
      PlayerBestiaCreateData(
        bestiaIdentifier = "doom_master_of_doom",
        spawnPosition = Vec3L.ZERO
      )
    )

    accountRepository.save(account1)

    return TestFixture.AccountData(
      account = account1,
      masterIds = listOf(
        master1.id,
        master2.id
      )
    )
  }

  fun createAccount2(): TestFixture.AccountData {
    val account2 = accountFactory.createAccount(2L)

    LOG.info { "Account 2 (ID: ${account2.id}) created" }

    val createMasterData = MasterFactory.CreateMasterData(
      name = "account2",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    val master = masterFactory.create(account2, createMasterData)

    return TestFixture.AccountData(
      account = account2,
      masterIds = listOf(
        master.id
      )
    )
  }

  fun createAccount3(): TestFixture.AccountData {
    val account3 = accountFactory.createAccount(3L)

    val createMasterData = MasterFactory.CreateMasterData(
      name = "account3",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    val master = masterFactory.create(account3, createMasterData)

    LOG.info { "Account 3 (ID: ${account3.id}) created" }

    return TestFixture.AccountData(
      account = account3,
      masterIds = listOf(
        master.id
      )
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
