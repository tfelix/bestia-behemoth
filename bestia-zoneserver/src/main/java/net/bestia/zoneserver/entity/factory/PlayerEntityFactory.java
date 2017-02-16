package net.bestia.zoneserver.entity.factory;

import net.bestia.model.dao.BestiaDAO;
import net.bestia.zoneserver.entity.EntityContext;
import net.bestia.zoneserver.service.EntityService;

public class PlayerEntityFactory extends EntityFactory {

	public PlayerEntityFactory(BestiaDAO bestiaDao, EntityContext entityCtx, EntityService entityService) {
		super(bestiaDao, entityCtx, entityService);
		// TODO Auto-generated constructor stub
	}

}
