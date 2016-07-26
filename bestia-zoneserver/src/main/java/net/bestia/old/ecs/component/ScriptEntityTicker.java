package net.bestia.zoneserver.ecs.component;

import com.artemis.Component;

import groovy.lang.Closure;

public class ScriptEntityTicker extends Component {
	
	public int interval;
	public float cooldown;
	public Closure<Void> fn;

}
