package searchengine.services;

import lombok.Builder;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.util.EntityService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Builder
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private EntityService entityService;
    private IndexingService indexingService;
    private final String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
    private final String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            ""
    };

    public StatisticsServiceImpl(SitesList sites, EntityService entityService, IndexingService indexingService) {
        this.sites = sites;
        this.entityService = entityService;
        this.indexingService = indexingService;
    }

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = TotalStatistics.builder()
                .sites(sites.getSites().size())
                .indexing(true)
                .build();

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for(int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            int pages = random.nextInt(1_000);
            int lemmas = pages * random.nextInt(1_000);
            DetailedStatisticsItem item = DetailedStatisticsItem.builder()
                    .name(site.getName())
                    .url(site.getUrl())
                    .pages(pages)
                    .lemmas(lemmas)
                    .status(statuses[i % 3])
                    .error(errors[i % 3])
                    .statusTime(System.currentTimeMillis() - (random.nextInt(10_000)))
                    .build();

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);

            detailed.add(item);
        }

        return getStatisticsResponse(total, detailed);
    }

    public StatisticsResponse getStatisticsResponse(TotalStatistics total, List<DetailedStatisticsItem> detailed) {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);

        return response;
    }

    /**
     * Метод, возвращающий статистику.
     * @return - статистика всех проиндексированных сайтов.
     */
    @Override
    public StatisticsResponse getStatisticsForResponse() {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        List<DetailedStatisticsItem> statisticsList = new ArrayList<>();

        for (searchengine.model.entity.Site site : entityService.getAllSites()) {
            DetailedStatisticsItem detailedStatisticsItem = DetailedStatisticsItem.builder()
                    .url(site.getUrl())
                    .name(site.getName())
                    .status(site.getStatus().toString())
                    .statusTime(convertDateToLong(site.getStatusTime()))
                    .error(site.getLastError())
                    .pages(entityService.getPagesCountBySite(site))
                    .lemmas(entityService.getLemmasCountBySite(site))
                    .build();

            statisticsList.add(detailedStatisticsItem);
        }

        TotalStatistics totalStatistics = TotalStatistics.builder()
                .sites(entityService.getAllSitesCount())
                .pages((int)entityService.getAllPagesCount())
                .lemmas(entityService.getAllLemmasCount())
                .indexing(indexingService.isIndexingStart())
                .build();

        data.setTotal(totalStatistics);
        data.setDetailed(statisticsList);

        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    /**
     * Метод, конвертирующий Date в long (нужен для статистики).
     * @param date - дата.
     * @return - количество миллисекунд с 1 января 1970 года.
     */
    public long convertDateToLong(LocalDateTime date) {
        ZonedDateTime zdt = ZonedDateTime.of(date, ZoneId.systemDefault());
        return zdt.toInstant().toEpochMilli();
    }
}
