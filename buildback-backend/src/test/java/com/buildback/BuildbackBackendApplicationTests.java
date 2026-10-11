package com.buildback;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// "test" profile = in-memory H2 database, so this runs without MySQL credentials
@SpringBootTest
@ActiveProfiles("test")
class BuildbackBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
