package net.bestia.zoneserver.proxy;

import com.artemis.ComponentMapper;

import net.bestia.model.ServiceLocator;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;

public class PlayerBestiaMapper extends BestiaMapper {
	
	public static class Builder extends BestiaMapper.Builder {
		private Zoneserver server;
		private ServiceLocator locator;
	}
	
	protected PlayerBestiaMapper(Builder builder) {
		super(builder);
		
		this.locator = builder.locator;
		this.server = builder.server;
		
	}

	private final Zoneserver server;
	private final ServiceLocator locator;
	
	public ServiceLocator getLocator() {
		return locator;
	}
	
	public Zoneserver getServer() {
		return server;
	}

	public PlayerBestiaSpawnManager getPlayerBestiaSpawnManager() {
		// TODO Auto-generated method stub
		return null;
	}

	public ComponentMapper<Attacks> getAttacksMapper() {
		// TODO Auto-generated method stub
		return null;
	}

}
