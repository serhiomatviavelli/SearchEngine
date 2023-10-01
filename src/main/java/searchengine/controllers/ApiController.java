package searchengine.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.IndexingFailedResponse;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.SearchSuccessResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.result.RelevancePage;
import searchengine.services.SiteService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final SiteService siteService;

    public ApiController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping(value = "/statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok().body(siteService.getStatistics());
    }

    @GetMapping(value = "/startIndexing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> startIndexing() {
        if(siteService.isIndexingStart()) {
            return ResponseEntity.badRequest().body(new IndexingFailedResponse( "Индексация уже запущена"));
        } else {
            siteService.startIndexing();
            return ResponseEntity.ok().body(new IndexingResponse());
        }
    }

    @GetMapping(value = "/stopIndexing", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> stopIndexing() {
        if (!siteService.isIndexingStart()) {
            return ResponseEntity.badRequest().body(new IndexingFailedResponse("Индексация не запущена"));
        }
        siteService.stopIndexing();
        return ResponseEntity.ok().body(new IndexingResponse());
    }

    @RequestMapping(value = "/indexPage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam("url") String url) throws IOException {
        if (siteService.getParentUrl(url) == null) {
            return ResponseEntity.badRequest().body(new IndexingFailedResponse(
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        }
        siteService.indexPage(url);
        return ResponseEntity.ok().body(new IndexingResponse());
    }

    @GetMapping(value = "/search")
    public Object search(@RequestParam(value = "query") String query,
                                                   @RequestParam(value = "site", required = false) String site,
                                                   @RequestParam(value = "offset", defaultValue = "0", required = false) int offset,
                                                   @RequestParam(value = "limit", defaultValue = "20", required = false) int limit) throws IOException {
        if (siteService.search(query, site, offset, limit) == null) {
            return ResponseEntity.badRequest().body(new IndexingFailedResponse(
                    "Задан пустой поисковый запрос"));
        } else if (siteService.search(query, site, offset, limit).isEmpty()) {
            return ResponseEntity.badRequest().body(new IndexingFailedResponse(
                    "Совпадения не найдены"));
        } else {
            List<RelevancePage> pages = siteService.search(query, site, offset, limit);
            return ResponseEntity.ok().body(new SearchSuccessResponse(true, pages.size(), siteService.getPagesForResponse(pages)));
        }
    }
}
