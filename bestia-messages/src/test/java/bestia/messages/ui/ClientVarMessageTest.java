package bestia.messages.ui;

import net.bestia.messages.ui.ClientVarResponseMessage;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ClientVarMessageTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final static long ACC_ID = 123;
	private final static long NEW_ACC_ID = 125;
	private final static String UUID = "test-123";
	private final static String JSON_DATA = "{\"key\": \"Bla\", \"value\": 1337}";

	@Test
	public void createNewInstance_newInstance() {
		ClientVarResponseMessage cvmsg1 = new ClientVarResponseMessage(ACC_ID, UUID, JSON_DATA);
		JsonMessage cvmsg2 = cvmsg1.createNewInstance(NEW_ACC_ID);
		
		Assert.assertNotEquals(cvmsg1, cvmsg2);
		Assert.assertEquals(NEW_ACC_ID, cvmsg2.getAccountId());
	}

	@Test
	public void canBeJsonSerialized() throws Exception {
		ClientVarResponseMessage cvmsg = new ClientVarResponseMessage(ACC_ID, UUID, JSON_DATA);
		String json = mapper.writeValueAsString(cvmsg);
		Assert.assertNotNull(json);
	}
}
