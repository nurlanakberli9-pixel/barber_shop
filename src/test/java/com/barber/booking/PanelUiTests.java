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
class PanelUiTests {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String page() throws Exception {
        return mvc.perform(get("/panel.html")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void panelUsesWindowEditorAndNoOldHourCreator() throws Exception {
        String html = page();
        assertTrue(html.contains("function windowsPanel"));
        assertTrue(html.contains("/api/panel/windows"));
        assertFalse(html.contains("Saat yarat"));
    }

    @Test
    void panelNeverUsesInnerHtml() throws Exception {
        assertFalse(page().contains("innerHTML"));
    }
}
