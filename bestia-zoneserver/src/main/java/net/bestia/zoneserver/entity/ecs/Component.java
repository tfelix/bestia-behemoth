package net.bestia.zoneserver.entity.ecs;

public interface Component {

	static int getComponentId() {
		throw new IllegalStateException("Must be impemented by child.");
	}
}
