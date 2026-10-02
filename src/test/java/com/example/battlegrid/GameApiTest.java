package com.example.battlegrid;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameApiTest {
    @Autowired MockMvc mvc;
    @Test void commandsAndSessionIsolation() throws Exception {
        MockHttpSession blue = new MockHttpSession(), other = new MockHttpSession();
        mvc.perform(get("/api/game").session(blue)).andExpect(status().isOk()).andExpect(jsonPath("$.energy").value(60));
        mvc.perform(post("/api/game/action").session(blue).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"MOVE\",\"unitId\":\"u2\",\"row\":5,\"col\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.energy").value(50));
        mvc.perform(get("/api/game").session(other)).andExpect(jsonPath("$.energy").value(60));
        mvc.perform(post("/api/game/action").session(blue).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"MOVE\",\"unitId\":\"u2\",\"row\":4,\"col\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/game/new").session(blue)).andExpect(status().isOk()).andExpect(jsonPath("$.energy").value(60));
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
