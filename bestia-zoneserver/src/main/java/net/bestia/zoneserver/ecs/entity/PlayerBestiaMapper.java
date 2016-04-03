package net.bestia.zoneserver.ecs.entity;

import com.artemis.ComponentMapper;
import com.artemis.managers.UuidEntityManager;

import net.bestia.model.ServiceLocator;
import net.bestia.zoneserver.Zoneserver;
import net.bestia.zoneserver.ecs.component.Attacks;
import net.bestia.zoneserver.ecs.component.PlayerBestia;
import net.bestia.zoneserver.ecs.manager.PlayerBestiaSpawnManager;
import net.bestia.zoneserver.proxy.PlayerBestiaEntityProxy;

/**
 * Mapper holder for the creation of {@link PlayerBestiaEntityProxy} objects.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class PlayerBestiaMapper extends BestiaMapper {

	public static class Builder extends BestiaMapper.Builder {
		private Zoneserver server;
		private ServiceLocator locator;
		private UuidEntityManager uuidManager;
		private ComponentMapper<Attacks> attacksMapper;
		private PlayerBestiaSpawnManager spawnManager;
		private ComponentMapper<PlayerBestia> playerBestiaMapper;
		
		public PlayerBestiaMapper build() {			
			return new PlayerBestiaMapper(this);
		}
		
		public void setServer(Zoneserver server) {
			this.server = server;
		}
		
		public void setLocator(ServiceLocator locator) {
			this.locator = locator;
		}
		
		public void setUuidManager(UuidEntityManager uuidManager) {
			this.uuidManager = uuidManager;
		}
		
		public void setAttacksMapper(ComponentMapper<Attacks> attacksMapper) {
			this.attacksMapper = attacksMapper;
		}
		
		public void setSpawnManager(PlayerBestiaSpawnManager spawnManager) {
			this.spawnManager = spawnManager;
		}
		
		public void setPlayerBestiaMapper(ComponentMapper<PlayerBestia> playerBestiaMapper) {
			this.playerBestiaMapper = playerBestiaMapper;
		}
	}
	
	private final Zoneserver server;
	private final ServiceLocator locator;
	private final UuidEntityManager uuidManager;
	private final PlayerBestiaSpawnManager spawnManager;
	private final ComponentMapper<Attacks> attacksMapper;
	private final ComponentMapper<PlayerBestia> playerBestiaMapper;

	protected PlayerBestiaMapper(Builder builder) {
		super(builder);
		
		if(builder.server == null) {
			throw new IllegalArgumentException("Server can not be null.");
		}
		if(builder.locator == null) {
			throw new IllegalArgumentException("Locator can not be null.");
		}
		if(builder.uuidManager == null) {
			throw new IllegalArgumentException("UuidManager can not be null.");
		}
		if(builder.attacksMapper == null) {
			throw new IllegalArgumentException("AttacksMapper can not be null.");
		}
		if(builder.spawnManager == null) {
			throw new IllegalArgumentException("SpawnManager can not be null.");
		}
		if(builder.playerBestiaMapper == null) {
			throw new IllegalArgumentException("PlayerBestiaMapper can not be null.");
		}

		this.locator = builder.locator;
		this.server = builder.server;
		this.uuidManager = builder.uuidManager;
		this.attacksMapper = builder.attacksMapper;
		this.spawnManager = builder.spawnManager;
		this.playerBestiaMapper = builder.playerBestiaMapper;

	}

	public ServiceLocator getLocator() {
		return locator;
	}

	public Zoneserver getServer() {
		return server;
	}
	
	public ComponentMapper<PlayerBestia> getPlayerBestiaMapper() {
		return playerBestiaMapper;
	}

	public PlayerBestiaSpawnManager getPlayerBestiaSpawnManager() {
		return spawnManager;
	}

	public ComponentMapper<Attacks> getAttacksMapper() {
		return attacksMapper;
	}
	
	public UuidEntityManager getUuidManager() {
		return uuidManager;
	}

}