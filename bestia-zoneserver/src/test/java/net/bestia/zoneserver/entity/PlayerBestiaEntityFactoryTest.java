package net.bestia.zoneserver.entity;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.util.Assert;

import net.bestia.model.domain.Bestia;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;

public class PlayerBestiaEntityFactoryTest {
	
	private final static int LEVEL = 10;
	private final static int EXP = 124;

	private PlayerBestiaEntityFactory factory;

	private Blueprint bestiaBP;
	private EntityFactory entityFactory;
	private Bestia bestia;
	private PlayerBestia playerBestia;
	private Point currentPos = new Point(12, 56);
	private SpriteInfo spriteInfo;

	@Before
	public void setup() {

		bestia = mock(Bestia.class);
		entityFactory = mock(EntityFactory.class);
		playerBestia = mock(PlayerBestia.class);
		spriteInfo = mock(SpriteInfo.class);
		
		
		when(bestia.getSpriteInfo()).thenReturn(spriteInfo);	
		
		when(playerBestia.getOrigin()).thenReturn(bestia);
		when(playerBestia.getLevel()).thenReturn(LEVEL);
		when(playerBestia.getExp()).thenReturn(EXP);
		when(playerBestia.getCurrentPosition()).thenReturn(currentPos);
		
		
		factory = new PlayerBestiaEntityFactory(entityFactory);
	}

	@Test(expected = NullPointerException.class)
	public void build_null_throws() {
		factory.build(null);
	}
	
	@Test
	public void build_validPlayerBestia_builds() {
		
		Entity e = factory.build(playerBestia);
		
		Assert.notNull(e);
		verify(entityFactory.build(bestiaBP, any()));
	}

}
