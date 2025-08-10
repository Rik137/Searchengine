package searchengine.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;

import searchengine.services.serviceinterfaces.IndexingService;
import searchengine.services.serviceinterfaces.StatisticsService;

import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
@Slf4j
public class ApiController {

    private final IndexingService indexingService;
    private final StatisticsService statisticsService;

    @Autowired
    public ApiController(IndexingService indexingService, StatisticsService statisticsService) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
    }
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> getStatistics() {
        log.info("GET /api/statistics");
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        log.info("GET /api/startIndexing");

        if (indexingService.isIndexing()) {
            log.warn("Попытка запустить индексацию при уже запущенном процессе");
            return error("Индексация уже запущена", HttpStatus.BAD_REQUEST);
        }

        indexingService.startIndexing();
        log.info("Индексация успешно запущена");
        return ResponseEntity.ok(new ApiResponse(true, null));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        log.info("GET /api/stopIndexing");

        if (!indexingService.isIndexing()) {
            log.warn("Попытка остановить индексацию, которая не запущена");
            return error("Индексация не запущена", HttpStatus.BAD_REQUEST);
        }

        indexingService.stopIndexing();
        log.info("Индексация остановлена пользователем");
        return ResponseEntity.ok(new ApiResponse(true, null));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> indexPage(@RequestParam("url") @NotBlank String url) {
        log.info("POST /api/indexPage?url={}", url);

        try {
            boolean success = indexingService.indexPage(url);
            if (!success) {
                return error("Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле", HttpStatus.BAD_REQUEST);
            }
            log.info("Страница успешно отправлена на индексацию");
            return ResponseEntity.ok(new ApiResponse(true, null));
        } catch (Exception e) {
            log.error("Ошибка при индексации страницы", e);
            return error("Ошибка индексации: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<ApiResponse> error(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(new ApiResponse(false, message));
    }
}



