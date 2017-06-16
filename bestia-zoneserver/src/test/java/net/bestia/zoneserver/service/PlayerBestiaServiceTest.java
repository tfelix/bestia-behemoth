package net.bestia.zoneserver.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.BestiaAttackDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.dao.PlayerItemDAO;
import net.bestia.model.domain.Account;
import net.bestia.model.domain.PlayerBestia;

/**
 * TODO Some methods are under work ind progress so they are not yet tested.
 * Will be when API finalized.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class PlayerBestiaServiceTest {

	private static final long WRONG_ACC_ID = 2;
	private static final long OK_ACC_ID = 1;
	private static final List<PlayerBestia> ALL_BESTIAS = new ArrayList<>();
	private static final long OK_PLAYERBESTIA_ID = 10;
	private static final long WRONG_PLAYERBESTIA_ID = 11;

	private PlayerBestiaService pbService;

	@Mock
	private AccountDAO accountDao;

	@Mock
	private PlayerBestiaDAO playerBestiaDao;

	@Mock
	private BestiaAttackDAO attackLevelDao;

	@Mock
	private PlayerItemDAO playerItemDao;

	@Mock
	private PlayerBestia playerBestia;

	@Mock
	private Account account;

	@Before
	public void setup() {
		ALL_BESTIAS.clear();
		ALL_BESTIAS.add(playerBestia);

		when(account.getMaster()).thenReturn(playerBestia);

		when(accountDao.findOne(OK_ACC_ID)).thenReturn(account);

		when(playerBestiaDao.findOne(OK_PLAYERBESTIA_ID)).thenReturn(playerBestia);

		pbService = new PlayerBestiaService(accountDao, playerBestiaDao, attackLevelDao, playerItemDao);
	}

	@Test
	public void getAllBestias_wrongAccId_empty() {
		Set<PlayerBestia> bestias = pbService.getAllBestias(WRONG_ACC_ID);
		assertThat(bestias, hasSize(0));
	}

	@Test
	public void getAllBestias_okAccId_allBestias() {
		Set<PlayerBestia> bestias = pbService.getAllBestias(OK_ACC_ID);

		assertThat(bestias, hasSize(ALL_BESTIAS.size()));
	}

	@Test
	public void getPlayerBestia_wrongId_null() {
		PlayerBestia bestia = pbService.getPlayerBestia(WRONG_PLAYERBESTIA_ID);

		assertThat(bestia, nullValue());
	}

	@Test
	public void getPlayerBestia_okId_bestia() {
		PlayerBestia bestia = pbService.getPlayerBestia(OK_PLAYERBESTIA_ID);

		assertThat(bestia, notNullValue());
	}

	@Test
	public void getMaster_wrongAccId_null() {
		PlayerBestia master = pbService.getMaster(WRONG_ACC_ID);

		assertThat(master, nullValue());
	}

	@Test
	public void getMaster_okAccId_master() {
		PlayerBestia master = pbService.getMaster(OK_ACC_ID);

		assertThat(master, notNullValue());
	}

	@Test(expected = NullPointerException.class)
	public void save_null_throws() {
		pbService.save(null);
	}

	@Test
	public void save_playerBestia_saved() {
		pbService.save(playerBestia);

		verify(playerBestiaDao).save(playerBestia);
	}
}
