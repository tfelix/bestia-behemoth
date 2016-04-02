package net.bestia.zoneserver.ecs.entity;

import com.artemis.ComponentMapper;

import net.bestia.zoneserver.ecs.component.Bestia;
import net.bestia.zoneserver.ecs.component.Movement;
import net.bestia.zoneserver.ecs.component.Position;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;
import net.bestia.zoneserver.ecs.component.StatusPoints;
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
public class BestiaMapper {

	private final ComponentMapper<PositionDomainProxy> positionProxyMapper;
	private final ComponentMapper<Position> positionMapper;
	private final ComponentMapper<Visible> visibleMapper;
	private final ComponentMapper<Bestia> bestiaMapper;
	private final ComponentMapper<Movement> movementMapper;
	private final ComponentMapper<StatusPoints> statusMapper;

	static class Builder {
		private ComponentMapper<PositionDomainProxy> positionProxyMapper;
		private ComponentMapper<Position> positionMapper;
		private ComponentMapper<Visible> visibleMapper;
		private ComponentMapper<Bestia> bestiaMapper;
		private ComponentMapper<Movement> movementMapper;
		private ComponentMapper<StatusPoints> statusMapper;

		public Builder() {

		}
		
		public void setStatusMapper(ComponentMapper<StatusPoints> statusMapper) {
			this.statusMapper = statusMapper;
		}

		public void setMovementMapper(ComponentMapper<Movement> movementMapper) {
			this.movementMapper = movementMapper;
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
			return new BestiaMapper(this);
		}
	}

	protected BestiaMapper(Builder builder) {

		if (builder.positionProxyMapper == null) {
			throw new IllegalArgumentException("PositionProxyMapper can not be null.");
		}
		if (builder.positionMapper == null) {
			throw new IllegalArgumentException("PositionMapper can not be null.");
		}
		if (builder.visibleMapper == null) {
			throw new IllegalArgumentException("VisibleMapper can not be null.");
		}
		if (builder.bestiaMapper == null) {
			throw new IllegalArgumentException("BestiaMapper can not be null.");
		}
		if (builder.movementMapper == null) {
			throw new IllegalArgumentException("MovementMapper can not be null.");
		}
		if(builder.statusMapper == null) {
			throw new IllegalArgumentException("StatusMapper can not be null.");
		}

		this.positionMapper = builder.positionMapper;
		this.positionProxyMapper = builder.positionProxyMapper;
		this.visibleMapper = builder.visibleMapper;
		this.bestiaMapper = builder.bestiaMapper;
		this.movementMapper = builder.movementMapper;
		this.statusMapper = builder.statusMapper;
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

	public ComponentMapper<Movement> getMovementMapper() {
		return movementMapper;
	}
	
	public ComponentMapper<StatusPoints> getStatusMapper() {
		return statusMapper;
	}

}
