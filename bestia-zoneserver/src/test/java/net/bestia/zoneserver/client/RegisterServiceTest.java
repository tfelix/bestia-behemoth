package net.bestia.zoneserver.client;

import net.bestia.zoneserver.config.StaticConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegisterServiceTest {

	private final static String RECAPTCHA_ALWAYS_VALID = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe";

	private RegisterService register;

	@Mock
	private StaticConfig config;

	@Before
	public void setup() {

		when(config.getReCaptchaSecretKey()).thenReturn(RECAPTCHA_ALWAYS_VALID);

		register = new RegisterService(config);
	}

	@Test
	public void isHumanUser_validKey_authenticates() {
		boolean result = register.isHumanUser("test123");
		Assert.assertTrue(result);
	}

}
