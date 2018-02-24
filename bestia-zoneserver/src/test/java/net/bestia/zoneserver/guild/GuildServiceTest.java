package net.bestia.zoneserver.guild;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.dao.GuildDAO;
import net.bestia.model.dao.GuildMemberDAO;
import net.bestia.model.dao.PlayerBestiaDAO;

@RunWith(MockitoJUnitRunner.class)
public class GuildServiceTest {

	private GuildService gs;
	
	@Mock
	private GuildDAO guildDao;
	
	@Mock
	private GuildMemberDAO guildMemberDao;
	
	@Mock
	private PlayerBestiaDAO bestiaDao;
	
	@Before
	public void setup() {
		gs = new GuildService(guildDao, guildMemberDao, bestiaDao);
	}

	public void addPlayerToGuild_existingGuildEnoughSpace_memberAdded() {
		
	}

	public void addPlayerToGuild_existingGuildFull_memberAdded() {

	}

	public void addPlayerToGuild_playerHasGuild_memberNotAdded() {

	}

	public void getMaxGuildMembers_notExistingGuild_0() {

	}

	public void getMaxGuildMembers_existingGuild_correctNumber() {

	}

	public void hasGuild_memberWithGuild_true() {

	}

	public void hasGuild_memberWithGuild_false() {

	}

	public void getGuildOfPlayer_memberHasNoGuild_empty() {

	}

	public void addExpTaxToGuild_notExistingPlayer_nothing() {

	}

	public void addExpTaxToGuild_negativeExp_nothing() {

	}

	public void addExpTaxToGuild_validPlayerAndExp_taxAdded() {

	}
	
	public void getNeededNextLevelExp_existingGuild_exp() {
		
	}
}
