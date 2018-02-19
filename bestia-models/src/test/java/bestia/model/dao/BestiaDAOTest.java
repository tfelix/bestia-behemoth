package bestia.model.dao;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import bestia.model.domain.Bestia;

@RunWith(SpringRunner.class)
@SpringBootTest
@DataJpaTest
public class BestiaDAOTest {
	
	private final static String DB_NAME = "blubber";
	
	@Autowired
	private BestiaDAO dao;
	
	@Before
	public void setup() {
		Bestia b = new Bestia(DB_NAME);
		dao.save(b);
	}

	@Test
	public void findByDatabaseName_findsBestia() {
		
		Bestia b = dao.findByDatabaseName(DB_NAME);
		Assert.assertNotNull(b);
	}
}
