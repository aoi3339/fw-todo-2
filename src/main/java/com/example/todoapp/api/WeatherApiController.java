package com.example.todoapp.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeatherApiController {

    private final WeatherClient weatherClient;

    public WeatherApiController(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    @GetMapping("/api/weather")
    public ResponseEntity<Map<String, Map<String, String>>> getWeather(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        WeatherClient.WeatherResult result = weatherClient.getWeather();
        Map<String, Map<String, String>> weather = new LinkedHashMap<>();
        result.weather().forEach((dateText, regions) -> {
            LocalDate date = LocalDate.parse(dateText);
            if ((from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))) {
                weather.put(dateText, regions);
            }
        });
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.unavailable()) {
            response.header("X-Weather-Unavailable", "true");
        }
        return response.body(weather);
    }
}
