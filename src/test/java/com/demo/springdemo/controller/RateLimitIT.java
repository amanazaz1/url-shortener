package com.demo.springdemo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rl;DB_CLOSE_DELAY=-1",
        "app.rate-limit-per-minute=2"})
@AutoConfigureMockMvc
class RateLimitIT {

    @Autowired MockMvc mvc;

    @Test
    void thirdWriteWithinTheWindowIsRejected() throws Exception {
        String body = "{\"url\":\"https://example.com\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }
}
