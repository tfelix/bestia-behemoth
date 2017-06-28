package net.bestia.messages.ui;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.messages.ui.ClientVarRequestMessage.Mode;

public class ClientVarRequestMessageTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private static final String KEY = "test";

	private static final String JSON_REQ = "{\"mid\": \"cvar.req\", \"m\": \"REQ\", \"key\": \"test\", \"uuid\": \"test-1234\"}";
	private static final String JSON_SET = "{\"mid\": \"cvar.req\", \"m\": \"SET\", \"key\": \"test\", \"d\": \"ThisIsData\"}";
	private static final String JSON_DEL = "{\"mid\": \"cvar.req\", \"m\": \"DEL\", \"key\": \"test\"}";

	@Test
	public void deserilaize_JsonReq_works() throws Exception {
		ClientVarRequestMessage msg = mapper.readValue(JSON_REQ, ClientVarRequestMessage.class);

		Assert.assertEquals(KEY, msg.getKey());
		Assert.assertNotNull(msg.getUuid());
	}

	@Test
	public void deserilaize_JsonSet_works() throws Exception {
		ClientVarRequestMessage msg = mapper.readValue(JSON_SET, ClientVarRequestMessage.class);

		Assert.assertEquals(KEY, msg.getKey());
		Assert.assertNotNull(msg.getData());
	}

	@Test
	public void deserilaize_JsonDel_works() throws Exception {
		ClientVarRequestMessage msg = mapper.readValue(JSON_DEL, ClientVarRequestMessage.class);

		Assert.assertEquals(KEY, msg.getKey());
		Assert.assertEquals(Mode.DEL, msg.getMode());
	}
}
