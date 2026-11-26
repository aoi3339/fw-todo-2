package com.example.todoapp.api;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WeatherClient {

    private static final URI WEATHER_URL = URI.create(
            "https://api.open-meteo.com/v1/forecast?latitude=35.6895,35.4437"
                    + "&longitude=139.6917,139.6380&daily=weather_code"
                    + "&timezone=Asia%2FTokyo&forecast_days=16");
    private static final List<String> REGIONS = List.of("東京", "神奈川");
    private final RestClient restClient;

    public WeatherClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    WeatherClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public WeatherResult getWeather() {
        try {
            List<Forecast> forecasts = restClient.get().uri(WEATHER_URL).retrieve()
                    .body(new ParameterizedTypeReference<List<Forecast>>() { });
            if (forecasts == null || forecasts.size() != REGIONS.size()) {
                throw new IllegalArgumentException("Unexpected weather locations");
            }
            Map<String, Map<String, String>> weather = new LinkedHashMap<>();
            for (int region = 0; region < REGIONS.size(); region++) {
                Forecast forecast = forecasts.get(region);
                Daily daily = forecast == null ? null : forecast.daily();
                if (daily == null || daily.time() == null || daily.weather_code() == null
                        || daily.time().size() != daily.weather_code().size()) {
                    throw new IllegalArgumentException("Unexpected daily weather");
                }
                for (int day = 0; day < daily.time().size(); day++) {
                    String date = daily.time().get(day);
                    if (date == null) {
                        throw new IllegalArgumentException("Missing weather date");
                    }
                    LocalDate.parse(date);
                    String description = describe(daily.weather_code().get(day));
                    if (description != null) {
                        weather.computeIfAbsent(date, ignored -> new LinkedHashMap<>())
                                .put(REGIONS.get(region), description);
                    }
                }
            }
            return new WeatherResult(weather, false);
        } catch (RestClientException | IllegalArgumentException | DateTimeException exception) {
            return new WeatherResult(Map.of(), true);
        }
    }

    private static String describe(Integer code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case 0, 1 -> "晴れ";
            case 2 -> "晴れ時々くもり";
            case 3 -> "くもり";
            case 45, 48 -> "霧";
            case 51, 53, 55 -> "霧雨";
            case 56, 57, 66, 67 -> "凍る雨";
            case 61, 63, 65, 80, 81, 82 -> "雨";
            case 71, 73, 75, 77, 85, 86 -> "雪";
            case 95, 96, 97, 99 -> "雷雨";
            default -> null;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(Daily daily) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(List<String> time, List<Integer> weather_code) { }

    public record WeatherResult(Map<String, Map<String, String>> weather, boolean unavailable) { }
}
