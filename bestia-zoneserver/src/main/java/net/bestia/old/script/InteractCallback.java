package net.bestia.zoneserver.script;

import net.bestia.zoneserver.proxy.Entity;

public interface InteractCallback {

	public void call(Entity owner, Entity caller);
	
}
