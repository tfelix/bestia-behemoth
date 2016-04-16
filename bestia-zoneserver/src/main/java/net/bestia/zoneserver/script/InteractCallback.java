package net.bestia.zoneserver.script;

import net.bestia.zoneserver.proxy.BestiaEntityProxy;

public interface InteractCallback {

	public void call(BestiaEntityProxy owner, BestiaEntityProxy caller);
	
}
