package net.bestia.zoneserver.proxy;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.Visible;

/**
 * <p>
 * This class contains all the mapper which are needed for a bestia to hook into
 * the ECS.
 * </p>
 * <p>
 * The class is constructed via a buildern pattern due to the many ctor
 * arguments.
 * </p>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class BestiaMapper {

	private final ComponentMapper<PositionDomainProxy> positionProxyMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<Bestia> bestiaMapper;

	static class Builder {
		private ComponentMapper<PositionDomainProxy> positionProxyMapper;
		private ComponentMapper<Position> positionMapper;
		private ComponentMapper<Visible> visibleMapper;
		private ComponentMapper<Bestia> bestiaMapper;

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

		public void setBestiaMapper(ComponentMapper<Bestia> bestiaMapper) {
			this.bestiaMapper = bestiaMapper;
		}

		/**
		 * Builds the mapper from the given mapper.
		 * 
		 * @return A BestiaMapper.
		 */
		public BestiaMapper build() {
			if (positionProxyMapper == null) {
				throw new IllegalArgumentException("PositionProxyMapper can not be null.");
			}
			if (positionMapper == null) {
				throw new IllegalArgumentException("PositionMapper can not be null.");
			}
			if (visibleMapper == null) {
				throw new IllegalArgumentException("VisibleMapper can not be null.");
			}
			if (bestiaMapper == null) {
				throw new IllegalArgumentException("BestiaMapper can not be null.");
			}

			return new BestiaMapper(this);
		}
	}

	protected BestiaMapper(Builder builder) {
		this.positionMapper = builder.positionMapper;
		this.positionProxyMapper = builder.positionProxyMapper;
		this.visibleMapper = builder.visibleMapper;
		this.bestiaMapper = builder.bestiaMapper;
	}

	public ComponentMapper<Bestia> getBestiaMapper() {
		return bestiaMapper;
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
