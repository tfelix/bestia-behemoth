package net.bestia.zoneserver.guild

import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import net.bestia.model.dao.GuildDAO
import net.bestia.model.dao.GuildMemberDAO
import net.bestia.model.dao.PlayerBestiaDAO

@RunWith(MockitoJUnitRunner::class)
class GuildServiceTest {

  private lateinit var gs: GuildService

  @Mock
  private lateinit var guildDao: GuildDAO

  @Mock
  private lateinit var guildMemberDao: GuildMemberDAO

  @Mock
  private lateinit var bestiaDao: PlayerBestiaDAO

  @Before
  fun setup() {
    gs = GuildService(guildDao, guildMemberDao, bestiaDao)
  }

  fun addPlayerToGuild_existingGuildEnoughSpace_memberAdded() {

  }

  fun addPlayerToGuild_existingGuildFull_memberAdded() {

  }

  fun addPlayerToGuild_playerHasGuild_memberNotAdded() {

  }

  fun getMaxGuildMembers_notExistingGuild_0() {

  }

  fun getMaxGuildMembers_existingGuild_correctNumber() {

  }

  fun hasGuild_memberWithGuild_true() {

  }

  fun hasGuild_memberWithGuild_false() {

  }

  fun getGuildOfPlayer_memberHasNoGuild_empty() {

  }

  fun addExpTaxToGuild_notExistingPlayer_nothing() {

  }

  fun addExpTaxToGuild_negativeExp_nothing() {

  }

  fun addExpTaxToGuild_validPlayerAndExp_taxAdded() {

  }

  fun getNeededNextLevelExp_existingGuild_exp() {

  }
}
