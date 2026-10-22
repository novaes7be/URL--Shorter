package com.contatodireto.shorturl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;


    @Container
    @ServiceConnection // liga ao datasource da aplicaçao automaticamente
    static PostgreSQLContainer sqlContainer = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> genericContainer = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static final String originalUrl = "https://google.com";



    @Test
    void shouldCreateShortUrl() throws Exception {
        String code = createShortUrl(originalUrl);

        assertTrue(code.matches("[a-zA-Z0-9]{7}"));
    }

    @Test
    void shouldRedirectToOriginalUrl() throws Exception {
        String code = createShortUrl(originalUrl);
        ResultActions getPerform = mockMvc.perform(get("/" + code));
        getPerform.andExpect(status().isFound());
        getPerform.andExpect(header().string("Location", originalUrl));
    }

    @Test
    void shouldReturn404WhenCodeNotFound() throws Exception {
        ResultActions performInexistente = mockMvc.perform(get("/codigoInexistente"));
        performInexistente.andExpect(status().isNotFound());
    }

    @Test
    void clickCounting() throws Exception {
        String code = createShortUrl(originalUrl);
        for (int i = 0; i < 3; i++ ) {
            mockMvc.perform(get("/" + code));
        }
        mockMvc.perform(get("/stats/" + code))
                .andExpect(
                        status().isOk())
                .andExpect(
                        content().string("3"));

    }

    @Test
    void cacheAside() throws Exception {
        String code  = createShortUrl(originalUrl);
        assertTrue(redisTemplate.hasKey("url:" + code));
        redisTemplate.delete("url:" + code);

        assertFalse(redisTemplate.hasKey("url:" + code));

        ResultActions perform = mockMvc.perform(get("/" + code));
        perform.andExpect(status().isFound());
        perform.andExpect(header().string("Location", originalUrl));

        assertTrue(redisTemplate.hasKey("url:" + code));
    }


    private String createShortUrl(String url) throws Exception {
        return mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}

