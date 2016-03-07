package net.bestia.zoneserver.proxy;

import net.bestia.model.domain.Location;
import net.bestia.zoneserver.ecs.component.PositionDomainProxy;

/**
 * This class wrapps an domain {@link Location} as well as an
 * 
 * @author Thomas
 *
 */
public class EcsLocationProxy extends Location {

	private static final long serialVersionUID = 1L;

	private final PositionDomainProxy positionProxy;

	public EcsLocationProxy(PositionDomainProxy positionProxy) {
		if (positionProxy == null) {
			throw new IllegalArgumentException("PositionProxy can not be null.");
		}

		this.positionProxy = positionProxy;
	}

	@Override
	public int getX() {

		return positionProxy.getPosition().getAnchor().x;

	}

	@Override
	public int getY() {

		return positionProxy.getPosition().getAnchor().y;

	}

	@Override
	public void setX(int x) {

		positionProxy.setX(x);

	}

	@Override
	public void setY(int y) {

		positionProxy.setX(y);

	}

}
