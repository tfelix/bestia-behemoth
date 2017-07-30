package net.bestia.zoneserver.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.zoneserver.configuration.StaticConfigService;

/**
 * Performs registrations of the user.
 * 
 * @author Thomas
 *
 */
@Service
public class RegisterService {
	
	private final static String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
	private final StaticConfigService config;
	
	@SuppressWarnings("unused")
	private static class RecaptchaResponse {
		
		public boolean success;
		public String challenge_ts;
		public String hostname;
		
		@JsonProperty("error-codes")
		public String errorCodes;
	}
	
	@Autowired
	public RegisterService(StaticConfigService config) {

		this.config = Objects.requireNonNull(config);
    }
	
	public boolean isHumanUser(String userResponse) {
		
		final RestTemplate rt = new RestTemplate();
		
		// Create a multimap to hold the named parameters		
		final MultiValueMap<String, String> parameters = new LinkedMultiValueMap<String, String>();
		parameters.add("secret", config.getReCaptchaSecretKey());
		parameters.add("response", userResponse);
	
        final RecaptchaResponse response = rt.postForObject(RECAPTCHA_VERIFY_URL, parameters, RecaptchaResponse.class);
        
        return response.success;
    }
}
