package net.bestia.zoneserver.ecs;

import java.util.HashMap;
import java.util.Map;

import net.bestia.zoneserver.manager.PlayerBestiaManager;

/**
 * Each zone has this registry which can be used to retrieve {@link PlayerBestiaManager}.
 * 
 * @author Thomas
 *
 */
public class PlayerBestiaRegistry {

	private final Map<Integer, PlayerBestiaManager> managers = new HashMap<>();
	private final Map<Long, Integer> activeBestias = new HashMap<>();
	
	public void addBestia();
	public void removeBestia();
	
	public void getActive();
	public void setActive();
	public void unsetActive();
}
