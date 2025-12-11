package com.krusty.crab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KrustyCrabApplicationTests {

	@Test
	void contextLoads() {
		// Тест проверяет, что Spring контекст загружается без ошибок
	}

}

