package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PanelPageTests {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void panelPageIsPublicButContainsNoData() throws Exception {
        var res = mvc.perform(get("/panel.html")).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("BarberConnect"));
    }

    @Test
    void otherStaticPathsAreDenied() throws Exception {
        assertEquals(401, mvc.perform(get("/secret.html")).andReturn().getResponse().getStatus());
    }
}
