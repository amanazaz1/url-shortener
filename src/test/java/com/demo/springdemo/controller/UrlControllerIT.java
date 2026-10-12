package com.demo.springdemo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.springdemo.entity.UrlMapping;
import com.demo.springdemo.repository.UrlMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:it;DB_CLOSE_DELAY=-1",
        "app.rate-limit-per-minute=10000"})
@AutoConfigureMockMvc
class UrlControllerIT {

    @Autowired MockMvc mvc;
    @Autowired UrlMappingRepository repository;
    @Autowired ObjectMapper json;

    private String create(String body) throws Exception {
        return mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createRedirectAndStats() throws Exception {
        JsonNode created = json.readTree(create("{\"url\":\"https://example.com/a?x=1\"}"));
        String code = created.get("shortCode").asText();

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/a?x=1"))
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(get("/api/v1/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(1));
    }

    /** Regression for the original bug: @Cacheable on the click-counting method skipped counts on cache hits. */
    @Test
    void everyRedirectIsCountedEvenWhenServedFromCache() throws Exception {
        String code = json.readTree(create("{\"url\":\"https://example.com/cache\"}")).get("shortCode").asText();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/" + code)).andExpect(status().isFound());
        }
        mvc.perform(get("/api/v1/urls/" + code + "/stats")).andExpect(jsonPath("$.clickCount").value(3));
    }

    @Test
    void customAliasAndConflict() throws Exception {
        create("{\"url\":\"https://example.com\",\"customAlias\":\"promo\"}");
        mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"customAlias\":\"promo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("alias_taken"));
    }

    @Test
    void unsafeUrlsAreRejected() throws Exception {
        for (String bad : new String[] {"javascript:alert(1)", "http://169.254.169.254/", "http://localhost/x"}) {
            mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_request"));
        }
    }

    @Test
    void missingUrlReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/shorten").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCodeIs404() throws Exception {
        mvc.perform(get("/doesnotexist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("link_not_found"));
    }

    @Test
    void expiredLinkIs410() throws Exception {
        UrlMapping m = new UrlMapping();
        m.setOriginalUrl("https://example.com/old");
        m.setShortCode("expired1");
        m.setExpiresAt(Instant.now().minusSeconds(5));
        repository.saveAndFlush(m);
        mvc.perform(get("/expired1")).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("link_expired"));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
