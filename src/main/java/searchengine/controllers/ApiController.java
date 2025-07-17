package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;

import searchengine.services.serviceinterfaces.IndexingService;
import searchengine.services.serviceinterfaces.StatisticsService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    @Autowired
    public ApiController(IndexingService indexingService,
                         StatisticsService statisticsService) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
    }


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();

        if (indexingService.isIndexing()) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return ResponseEntity.badRequest().body(response);
        }

        boolean started = indexingService.startIndexing();
        if (started) {
            response.put("result", true);
            return ResponseEntity.ok(response);
        } else {
            response.put("result", false);
            response.put("error", "Не удалось запустить индексацию");
            return ResponseEntity.status(500).body(response);
        }
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> response = new HashMap<>();

        if (!indexingService.isIndexing()) {
            response.put("result", false);
            response.put("error", "Индексация не запущена");
            return ResponseEntity.badRequest().body(response);
        }

        boolean stopped = indexingService.stopIndexing();
        if (stopped) {
            response.put("result", true);
            return ResponseEntity.ok(response);
        } else {
            response.put("result", false);
            response.put("error", "Не удалось остановить индексацию");
            return ResponseEntity.status(500).body(response);
        }
    }
}



