package com.flashdrop.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "INTERNAL_API_KEY=dev-key")
@ActiveProfiles("local")
class CatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
