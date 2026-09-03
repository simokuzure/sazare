package com.sazare;

import com.sazare.service.ai.client.AiProviderHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class LearningApplicationTests {

	@MockitoBean
	private AiProviderHttpClient aiProviderHttpClient;

	@Test
	void contextLoads() {
	}

}
