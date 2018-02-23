package net.bestia.model.dao;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.model.domain.TilesetData;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class TilesetDataDAOTest {
	
	@Autowired
	private TilesetDataDAO dao;
	
	@Before
	public void setup() {
		TilesetData ts = new TilesetData();
		ts.setId(1);
		ts.setMaxGid(100);
		ts.setMinGid(0);
		dao.save(ts);
	}
	
	@Test
	public void findByGid_existingGid_returns() {
		TilesetData ts1 = dao.findByGid(10);
		TilesetData ts2 = dao.findByGid(100);
		
		Assert.assertNotNull(ts1);
		Assert.assertEquals(ts1, ts2);
	}
	
	@Test
	public void findByGid_outOfRangeGid_null() {
		TilesetData ts1 = dao.findByGid(101);
		
		Assert.assertNull(ts1);
	}

}
