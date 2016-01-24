package net.bestia.zoneserver.script;

import java.util.concurrent.Callable;

import com.artemis.World;

import net.bestia.zoneserver.command.CommandContext;

/**
 * This class is a facade which wraps different calls to the ECS system. It is
 * also responsible for binding function calls into appropriate bindings to be
 * used inside of map scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapScriptAPI {

	/**
	 * Setup the entity which calls the callable.
	 * 
	 * @param delay
	 * @param fn
	 */
	public void setTimer(long delay, Callable<Void> fn) {
		
	}

	public void initWorld(World world) {
		// TODO Auto-generated method stub
		
	}

	public void initContext(CommandContext ctx) {
		// TODO Auto-generated method stub
		
	}
}
