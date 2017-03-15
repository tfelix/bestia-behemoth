package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tfelix.bestia.worldgen.MapMasterCallbacks;
import de.tfelix.bestia.worldgen.MapMasterGenerator;
import de.tfelix.bestia.worldgen.description.Map2DDescription;
import de.tfelix.bestia.worldgen.io.MasterCom;
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder;
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider;
import net.bestia.model.dao.MapDataDAO;
import net.bestia.zoneserver.map.MapBaseParameter;
import net.bestia.zoneserver.map.MapGeneratorConstants;

@Service
public class MapGeneratorClientService {

	private final static Logger LOG = LoggerFactory.getLogger(MapGeneratorClientService.class);

	
}
