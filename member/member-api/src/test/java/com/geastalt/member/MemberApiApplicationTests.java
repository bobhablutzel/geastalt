package com.geastalt.member;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"grpc.server.port=0"})
class MemberApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
