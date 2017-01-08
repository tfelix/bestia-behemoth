package net.bestia.zoneserver.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.model.dao.AccountDAO;
import net.bestia.model.dao.PlayerBestiaDAO;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Rect;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;
import net.bestia.zoneserver.actor.zone.ZoneActor;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.Visible;
import net.bestia.zoneserver.service.EntityService;

/**
 * Starts the actor system to process bestia messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
public class ZoneStarter2 implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ZoneStarter2.class);

	private EntityService service;
	private PlayerBestiaDAO dao;
	
	@Autowired
	public ZoneStarter2(EntityService service, PlayerBestiaDAO dao) {
		
		this.service = service;
		this.dao = dao;
	}

	@Override
	public void run(String... strings) throws Exception {
		
		PlayerBestia pb = dao.findOne(1);
		
		PlayerBestiaEntity pbe = new PlayerBestiaEntity(pb);
		service.save(pbe);
		
		service.getEntitiesInRange(new Rect(0, 0, 50, 50), Visible.class);
	}
}
