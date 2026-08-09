package com.synx.devkit;

import com.synx.devkit.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DevKitApplicationTests extends PostgresTestSupport {

    @Test
    void contextLoads() {
    }

}
