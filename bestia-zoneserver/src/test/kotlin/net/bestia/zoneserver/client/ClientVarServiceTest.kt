package net.bestia.zoneserver.client

import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.ClientVar
import net.bestia.model.account.ClientVarRepository
import net.bestia.zoneserver.account.ClientVarService
import org.junit.Assert
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class ClientVarServiceTest {

  @Rule
  @JvmField
  val rule = MockitoJUnit.rule().silent()

  @Mock
  private lateinit var accDao: AccountRepository

  @Mock
  private lateinit var cvarDao: ClientVarRepository

  @Mock
  private lateinit var account: Account

  @Mock
  private lateinit var existingCvar: ClientVar

  private lateinit var cvarService: ClientVarService

  @BeforeEach
  fun setup() {
    whenever(accDao.findById(EXISTING_ACC_ID)).thenReturn(Optional.of(account))
    whenever(accDao.findById(NOT_EXISTING_ACC)).thenReturn(null)
    whenever(cvarDao.findByKeyAndAccountId(EXISTING_KEY, OWNING_ACC_ID)).thenReturn(existingCvar)

    cvarService = ClientVarService(cvarDao, accDao)
  }

  @Test
  fun isOwnerOfVar_nonOwnerAccId_false() {
    Assert.assertFalse(cvarService.isOwnerOfVar(NON_OWNING_ACC_ID, EXISTING_KEY))
  }

  @Test
  fun isOwnerOfVar_notExistingAccId_false() {
    Assert.assertFalse(cvarService.isOwnerOfVar(NOT_EXISTING_ACC, EXISTING_KEY))
  }

  @Test
  fun isOwnerOfVar_owningAccId_true() {
    Assert.assertTrue(cvarService!!.isOwnerOfVar(OWNING_ACC_ID, EXISTING_KEY))
  }

  @Test
  fun delete_existingAccId_deletes() {
    cvarService.delete(EXISTING_ACC_ID, EXISTING_KEY)
    verify<ClientVarRepository>(cvarDao).deleteByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID)
  }

  @Test
  fun find_existingAccId_notExistingKey_null() {
    val `var` = cvarService.find(EXISTING_ACC_ID, NOT_EXISTING_KEY)
    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID)
    Assert.assertNull(`var`)
  }

  @Test
  fun set_longData_throws() {
    Assertions.assertThrows(IllegalArgumentException::class.java) {
      cvarService[EXISTING_ACC_ID, EXISTING_KEY] = LONG_DATA_STR
    }
  }

  @Test
  fun set_nonExistingAccountExistingDataAndKey_throws() {
    Assertions.assertThrows(IllegalArgumentException::class.java) {
      cvarService[NOT_EXISTING_ACC, EXISTING_KEY] = DATA_STR
    }
  }

  @Test
  fun set_existingAccountExistingDataAndKey_works() {

    cvarService[EXISTING_ACC_ID, EXISTING_KEY] = DATA_STR

    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID)
    verify<ClientVarRepository>(cvarDao).save(any(ClientVar::class.java))
  }

  @Test
  fun set_existingAccountNotExistingDataAndKey_works() {

    cvarService[EXISTING_ACC_ID, NOT_EXISTING_KEY] = DATA_STR

    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID)
    verify<ClientVarRepository>(cvarDao).save(any(ClientVar::class.java))
  }

  companion object {

    private const val EXISTING_ACC_ID: Long = 13
    private const val NON_OWNING_ACC_ID: Long = 12
    private const val OWNING_ACC_ID: Long = 14
    private const val NOT_EXISTING_ACC: Long = 10

    private const val EXISTING_KEY = "hello"
    private const val NOT_EXISTING_KEY = "blub"

    private val DATA_STR = "{\"bla\": 123, \"slot\": 10}"
    private val LONG_DATA_STR: String

    init {
      val builder = StringBuilder()
      for (i in 0..9999) {
        builder.append("A")
      }
      LONG_DATA_STR = builder.toString()
    }
  }
}
