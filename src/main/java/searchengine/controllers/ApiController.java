package searchengine.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingFailedResponse;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.SearchSuccessResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.result.RelevancePage;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final IndexingService indexingService;

    private final SearchService searchService;

    private final StatisticsService statisticsService;

        public ApiController(IndexingService indexingService, SearchService searchService, StatisticsService statisticsService) {
            this.indexingService = indexingService;
            this.searchService = searchService;
            this.statisticsService = statisticsService;
        }

    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok().body(statisticsService.getStatisticsForResponse());
    }

    @GetMapping(value = "/startIndexing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> startIndexing() {
        if(indexingService.isIndexingStart()) {
            return ResponseEntity.ok().body(new IndexingFailedResponse( "Индексация уже запущена"));
        } else {
            indexingService.startIndexing();
            return ResponseEntity.ok().body(new IndexingResponse());
        }
    }

    @GetMapping(value = "/stopIndexing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> stopIndexing() {
        if (!indexingService.isIndexingStart()) {
            return ResponseEntity.ok().body(new IndexingFailedResponse("Индексация не запущена"));
        }
        indexingService.stopIndexing();
        return ResponseEntity.ok().body(new IndexingResponse());
    }

    @RequestMapping(value = "/indexPage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam("url") String url) throws IOException {
        if (indexingService.getParentUrl(url) == null) {
            return ResponseEntity.ok().body(new IndexingFailedResponse(
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        }
        indexingService.indexPage(url);
        return ResponseEntity.ok().body(new IndexingResponse());
    }

    @GetMapping(value = "/search")
    public Object search(@RequestParam(value = "query") String query,
                                                   @RequestParam(value = "site", required = false) String site,
                                                   @RequestParam(value = "offset", defaultValue = "0", required = false) int offset,
                                                   @RequestParam(value = "limit", defaultValue = "20", required = false) int limit) {
        List<RelevancePage> result = searchService.search(query, site, offset, limit);
        if (query.length() == 0) {
            return ResponseEntity.ok().body(new IndexingFailedResponse(
                    "Задан пустой поисковый запрос"));
        } else if (result.isEmpty()) {
            return ResponseEntity.ok().body(new IndexingFailedResponse(
                    "Совпадения не найдены"));
        } else {
            return ResponseEntity.ok()
                    .body(new SearchSuccessResponse(true, result.size(),
                            searchService.getPagesForResponse(result)
                                    .subList(offset,
                                            (offset + limit > result.size()) ? offset + result.size() % limit : offset + limit)));

        }
    }
}
