package net.bestia.model.service;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import net.bestia.model.domain.PlayerBestia;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/spring-config.xml" })
public class PlayerBestiaServiceTest {
	
	@Autowired
	private PlayerBestiaService service;

	@Test(expected=IllegalArgumentException.class)
	public void savePlayerBestiaECS_null_exception() {
		service.savePlayerBestiaECS(null);
	}
	
	@Test
	public void getAllBesitas_wrongId_null() {
		final Set<PlayerBestia> result = service.getAllBestias(1337);
		Assert.assertNull(result);
	}
	
	public void savePlayerBestiaECS_ok() {
	
		
	}
}
