package net.bestia.zoneserver.proxy;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.Visible;

class BestiaMapper {

	private final ComponentMapper<PositionDomainProxy> positionProxyMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;

	static class Builder {
		private ComponentMapper<PositionDomainProxy> positionProxyMapper;
		private ComponentMapper<Position> positionMapper;
		private ComponentMapper<Visible> visibleMapper;
		
		public Builder() {
			
		}
		
		public void setPositionMapper(ComponentMapper<Position> positionMapper) {
			this.positionMapper = positionMapper;
		}
		
		public void setPositionProxyMapper(ComponentMapper<PositionDomainProxy> positionProxyMapper) {
			this.positionProxyMapper = positionProxyMapper;
		}
		
		public void setVisibleMapper(ComponentMapper<Visible> visibleMapper) {
			this.visibleMapper = visibleMapper;
		}
		
		public BestiaMapper build() {
			return new BestiaMapper(this);
		}
	}
	
	protected BestiaMapper(Builder builder) {
		this.positionMapper = builder.positionMapper;
		this.positionProxyMapper = builder.positionProxyMapper;
		this.visibleMapper = builder.visibleMapper;
	}
	
	public ComponentMapper<Position> getPositionMapper() {
		return positionMapper;
	}
	
	public ComponentMapper<PositionDomainProxy> getPositionProxyMapper() {
		return positionProxyMapper;
	}
	
	public ComponentMapper<Visible> getVisibleMapper() {
		return visibleMapper;
	}
	
}
