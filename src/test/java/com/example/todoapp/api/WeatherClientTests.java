package com.example.todoapp.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WeatherClientTests {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final WeatherClient client = new WeatherClient(builder.build());

    @Test
    void mapsBothLocationsAndFiltersDatesInclusively() {
        server.expect(anything()).andRespond(withSuccess("""
                [{"latitude":35.69,"daily":{"time":["2026-11-26","2026-11-27"],"weather_code":[0,3]}},
                 {"latitude":35.44,"daily":{"time":["2026-11-26","2026-11-27"],"weather_code":[61,95]}}]
                """, MediaType.APPLICATION_JSON));
        var response = new WeatherApiController(client).getWeather(
                LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 27));
        assertEquals(Map.of("2026-11-27", Map.of("東京", "くもり", "神奈川", "雷雨")), response.getBody());
        assertNull(response.getHeaders().getFirst("X-Weather-Unavailable"));
        server.verify();
    }

    @Test
    void networkFailureReturnsEmptySuccessfulResponse() {
        server.expect(anything()).andRespond(withException(new IOException("offline")));
        var response = new WeatherApiController(client).getWeather(null, null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of(), response.getBody());
        assertEquals("true", response.getHeaders().getFirst("X-Weather-Unavailable"));
        server.verify();
    }

    @Test
    void upstreamErrorReturnsUnavailable() {
        server.expect(anything()).andRespond(withServerError());
        var result = client.getWeather();
        assertTrue(result.unavailable());
        assertTrue(result.weather().isEmpty());
        server.verify();
    }

    @Test
    void malformedDataReturnsUnavailable() {
        server.expect(anything()).andRespond(withSuccess("""
                [{"daily":{"time":["invalid-date"],"weather_code":[0]}},
                 {"daily":{"time":["2026-11-26"],"weather_code":[61]}}]
                """, MediaType.APPLICATION_JSON));
        assertTrue(client.getWeather().unavailable());
        server.verify();
    }
}
