package net.bestia.zoneserver.service;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.bestia.model.domain.PlayerBestia;
import net.bestia.zoneserver.service.PlayerBestiaService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
public class PlayerBestiaServiceTest {
	
	@Autowired
	private PlayerBestiaService service;

	@Test
	public void getAllBesitas_wrongId_null() {
		final Set<PlayerBestia> result = service.getAllBestias(1337);
		Assert.assertNull(result);
	}
}
