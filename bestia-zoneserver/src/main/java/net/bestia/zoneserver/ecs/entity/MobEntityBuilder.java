package net.bestia.zoneserver.ecs.entity;

import net.bestia.model.domain.Bestia;

public class MobEntityBuilder extends EntityBuilder {
	
	Bestia bestia;
	
	String groupName;
	
	public void setBestia(Bestia bestia) {
		this.bestia = bestia;
	}
	
	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

}
