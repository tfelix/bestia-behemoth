package net.bestia.zoneserver.client

import net.bestia.model.account.AccountRepository
import net.bestia.model.account.ClientVarRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.Account
import net.bestia.model.account.ClientVar
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ClientVarServiceTest {

  @Mock
  private lateinit var accDao: AccountRepository

  @Mock
  private lateinit var cvarDao: ClientVarRepository

  @Mock
  private lateinit var account: Account

  @Mock
  private lateinit var existingCvar: ClientVar

  private var cvarService: ClientVarService? = null

  @Before
  fun setup() {

    `when`(accDao.findOneOrThrow(EXISTING_ACC_ID)).thenReturn(account)
    `when`(accDao.findOneOrThrow(NOT_EXISTING_ACC)).thenReturn(null)
    `when`(cvarDao.findByKeyAndAccountId(EXISTING_KEY, OWNING_ACC_ID)).thenReturn(existingCvar)

    cvarService = ClientVarService(cvarDao, accDao!!)
  }

  @Test(expected = NullPointerException::class)
  fun ctor_nullCvarDao_throws() {
    ClientVarService(null!!, accDao!!)
  }

  @Test(expected = NullPointerException::class)
  fun ctor_nullAccountDao_throws() {
    ClientVarService(cvarDao!!, null!!)
  }

  @Test
  fun isOwnerOfVar_nonOwnerAccId_false() {
    Assert.assertFalse(cvarService!!.isOwnerOfVar(NON_OWNING_ACC_ID, EXISTING_KEY))
  }

  @Test
  fun isOwnerOfVar_notExistingAccId_false() {
    Assert.assertFalse(cvarService!!.isOwnerOfVar(NOT_EXISTING_ACC, EXISTING_KEY))
  }

  @Test
  fun isOwnerOfVar_owningAccId_true() {
    Assert.assertTrue(cvarService!!.isOwnerOfVar(OWNING_ACC_ID, EXISTING_KEY))
  }

  @Test
  fun delete_existingAccId_deletes() {
    cvarService!!.delete(EXISTING_ACC_ID, EXISTING_KEY)
    verify<ClientVarRepository>(cvarDao).deleteByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID)
  }

  @Test
  fun find_existingAccId_notExistingKey_null() {
    val `var` = cvarService!!.find(EXISTING_ACC_ID, NOT_EXISTING_KEY)
    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID)
    Assert.assertNull(`var`)
  }

  @Test
  fun find_existingAccIdAndKey_cvar() {

  }

  @Test(expected = NullPointerException::class)
  fun set_nullKey_throws() {
    cvarService!![EXISTING_ACC_ID, null!!] = DATA_STR
  }

  @Test(expected = NullPointerException::class)
  fun set_nullData_throws() {
    cvarService!![EXISTING_ACC_ID, EXISTING_KEY] = null!!
  }

  @Test(expected = IllegalArgumentException::class)
  fun set_longData_throws() {
    cvarService!![EXISTING_ACC_ID, EXISTING_KEY] = LONG_DATA_STR
  }

  @Test(expected = IllegalArgumentException::class)
  fun set_nonExistingAccountExistingDataAndKey_throws() {
    cvarService!![NOT_EXISTING_ACC, EXISTING_KEY] = DATA_STR
  }

  @Test
  fun set_existingAccountExistingDataAndKey_works() {

    cvarService!![EXISTING_ACC_ID, EXISTING_KEY] = DATA_STR

    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(EXISTING_KEY, EXISTING_ACC_ID)
    verify<ClientVarRepository>(cvarDao).save(any(ClientVar::class.java))
  }

  @Test
  fun set_existingAccountNotExistingDataAndKey_works() {

    cvarService!![EXISTING_ACC_ID, NOT_EXISTING_KEY] = DATA_STR

    verify<ClientVarRepository>(cvarDao).findByKeyAndAccountId(NOT_EXISTING_KEY, EXISTING_ACC_ID)
    verify<ClientVarRepository>(cvarDao).save(any(ClientVar::class.java))
  }

  companion object {

    private val EXISTING_ACC_ID: Long = 13
    private val NON_OWNING_ACC_ID: Long = 12
    private val OWNING_ACC_ID: Long = 14
    private val NOT_EXISTING_ACC: Long = 10

    private val EXISTING_KEY = "hello"
    private val NOT_EXISTING_KEY = "blub"

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
