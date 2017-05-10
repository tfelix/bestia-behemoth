package net.bestia.zoneserver.service;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.dao.AccountDAO;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.configuration.RuntimeConfigurationService;
import net.bestia.zoneserver.entity.EntityServiceContext;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.PlayerBestiaService;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class LoginServiceTest {

	@MockBean
	private RuntimeConfigurationService config;

	@MockBean
	private AccountDAO accountDao;

	@MockBean
	private ConnectionService connectionService;

	@MockBean
	private EntityServiceContext entityServiceCtx;

	@MockBean
	private PlayerBestiaService playerBestiaService;

	@MockBean
	private ZoneAkkaApi akkaApi;

	//@Autowired
	//private PlayerBestiaEntityFactory playerEntityFactory;

	//private LoginService loginService;

	@Before
	public void setup() {
		
	}
}
