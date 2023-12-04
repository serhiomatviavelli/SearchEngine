package searchengine.services;

import lombok.Builder;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.repository.LemmaRepository;
import searchengine.model.repository.PageRepository;
import searchengine.model.repository.SearchingIndexRepository;
import searchengine.model.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private IndexingService indexingService;
    private final SearchingIndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    public StatisticsServiceImpl(IndexingService indexingService, SearchingIndexRepository indexRepository, LemmaRepository lemmaRepository, PageRepository pageRepository, SiteRepository siteRepository) {
        this.indexingService = indexingService;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
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

        for (searchengine.model.entity.Site site : siteRepository.findAll()) {
            DetailedStatisticsItem detailedStatisticsItem = DetailedStatisticsItem.builder()
                    .url(site.getUrl())
                    .name(site.getName())
                    .status(site.getStatus().toString())
                    .statusTime(convertDateToLong(site.getStatusTime()))
                    .error(site.getLastError())
                    .pages(pageRepository.findBySite(site).size())
                    .lemmas(lemmaRepository.findBySite(site).size())
                    .build();

            statisticsList.add(detailedStatisticsItem);
        }

        TotalStatistics totalStatistics = TotalStatistics.builder()
                .sites((int)siteRepository.count())
                .pages((int)pageRepository.count())
                .lemmas((int)lemmaRepository.count())
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
