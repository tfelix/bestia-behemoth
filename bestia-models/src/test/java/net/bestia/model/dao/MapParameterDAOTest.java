package net.bestia.model.dao;

import net.bestia.model.map.MapParameterRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


import net.bestia.model.map.MapParameter;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class MapParameterDAOTest {
	
	private static final String MAPNAME1 = "Ballermann";
	private static final String MAPNAME2 = "Ballermann 2";
	
	@Autowired
	private MapParameterRepository dao;
	
	@Before
	public void setup() {
		MapParameter p1 = MapParameter.Companion.fromAverageUserCount(100, MAPNAME1);
		dao.save(p1);
		
		MapParameter p2 = MapParameter.Companion.fromAverageUserCount(110, MAPNAME2);
		dao.save(p2);
	}
	
	@Test
	public void findLatest_latestParams() {
		MapParameter p = dao.findFirstByOrderByIdDesc();
		Assert.assertEquals(MAPNAME2, p.getName());
	}
}
