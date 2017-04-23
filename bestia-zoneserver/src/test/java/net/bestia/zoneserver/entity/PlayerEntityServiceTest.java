package net.bestia.zoneserver.entity;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import net.bestia.zoneserver.BasicMocks;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PlayerEntityServiceTest {
	
	@Autowired
	private PlayerEntityService pbeService;
	
	@MockBean
	private EntityService entityService;
	
	@MockBean
	private ZoneAkkaApi zoneAkkaApi;
	
	@Before
    public void setup() {
       // given(this.entityService.getVehicleDetails("123")
        //).willReturn(new VehicleDetails("Honda", "Civic"));
        
        
    }

	//public void getNew
}
