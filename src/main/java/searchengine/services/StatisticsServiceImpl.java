package searchengine.services;

import lombok.Builder;
import org.springframework.stereotype.Service;
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

@Builder
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private EntityService entityService;
    private IndexingService indexingService;

    public StatisticsServiceImpl(EntityService entityService, IndexingService indexingService) {
        this.entityService = entityService;
        this.indexingService = indexingService;
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
