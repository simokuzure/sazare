package com.sazare;

import com.sazare.service.ai.client.AiProviderHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartResolver;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class LearningApplicationTests {

	@MockitoBean
	private AiProviderHttpClient aiProviderHttpClient;

    @Autowired
    private ApplicationContext context;

    @Test
    void multipartParsingShouldRemainDisabled() {
        assertThat(context.getBeansOfType(MultipartResolver.class)).isEmpty();
    }

	@Test
	void contextLoads() {
	}

}
