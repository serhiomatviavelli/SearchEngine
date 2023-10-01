package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.result.RelevancePage;
import searchengine.dto.result.RelevancePageForResponse;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;
import searchengine.model.entity.Site;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Основной сервисный класс.
 */
@Service
public class SiteService {

    @Autowired
    private SitesList sitesList;

    @Autowired
    private Lemmatisator lemmatisator;

    @Autowired
    private EntityService entityService;

    private boolean indexingStart = false;
    private boolean indexingStop = false;

    /**
     * Метод, начинающий индексацию сайтов.
     */
    public void startIndexing() {
        indexingStart = true;

        entityService.deleteAllData();

        entityService.addIndexingSites(sitesList);

        for (Site site : entityService.getAllSites()) {
            try {
                WebSiteTree webSiteTree = new WebSiteTree(site.getUrl());
                RecursivePageWalker recursivePageWalker = new RecursivePageWalker(webSiteTree, this);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.invoke(recursivePageWalker);
                if (indexingStop) {
                    forkJoinPool.shutdownNow();
                    entityService.stopIndexingInfoAdd();
                    indexingStop = false;
                    break;
                } else {
                    entityService.saveIndexedSiteInfo(site);
                }
            } catch (Exception e) {
                entityService.saveFailedIndexingSiteInfo(site, e.getMessage());
            }
        }
        indexingStart = false;
    }

    /**
     * Метод, завершающий индексацию сайтов.
     */
    public void stopIndexing() {
        if (indexingStart) {
            indexingStop = true;
        }
    }

    /**
     * Метод, индексирующий заданную страницу по url.
     * @param url - url страницы, которую необходимо проиндексировать.
     * @throws IOException
     */
    public void indexPage(String url) throws IOException {
        List<Page> pageByUrl = entityService.getPagesByPath(url);
        if (pageByUrl.size() != 0 &&
            entityService.getIndexesCountByPage(pageByUrl.get(0)) != 0) {
            entityService.deleteIndexesLemmasAndPages();
        }

        String parentUrl = entityService.getParentUrl(url);

        if (parentUrl != null) {
            Connection.Response response = Jsoup.connect(url).execute();

            if (response.statusCode() == 200) {
                Site site = entityService.getSiteByUrl(parentUrl);
                Page page = entityService.saveNewPage(site, url, response.statusCode(), response.body());

                HashMap<String, Integer> lemmas = getLemmasList(response.body());
                for (Map.Entry<String, Integer> map : lemmas.entrySet()) {
                    List<Lemma> lemmaByLemma = entityService.getLemmasByLemma(map.getKey());
                    if (!lemmaByLemma.isEmpty()) {
                        Lemma lemma = entityService.getLemmaByLemma(map.getKey());
                        lemma.setFrequency(lemma.getFrequency() + 1);
                        entityService.saveLemma(lemma);
                    } else {
                        entityService.saveNewLemma(site, map.getKey());
                    }
                    entityService.saveNewIndex(page, entityService.getLemmaByLemma(map.getKey()), map.getValue().floatValue());
                }
                entityService.saveSiteDate(site);
            }
        }
    }

    /**
     * Метод, возвращающий статистику.
     * @return - статистика всех проиндексированных сайтов.
     */
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        List<DetailedStatisticsItem> statisticsList = new ArrayList<>();

        for (Site site : entityService.getAllSites()) {
            DetailedStatisticsItem detailedStatisticsItem = new DetailedStatisticsItem();

            detailedStatisticsItem.setUrl(site.getUrl());
            detailedStatisticsItem.setName(site.getName());
            detailedStatisticsItem.setStatus(site.getStatus().toString());
            detailedStatisticsItem.setStatusTime(convertDateToLong(site.getStatusTime()));
            detailedStatisticsItem.setError(site.getLastError());
            detailedStatisticsItem.setPages(entityService.getPagesCountBySite(site));
            detailedStatisticsItem.setLemmas(entityService.getLemmasCountBySite(site));

            statisticsList.add(detailedStatisticsItem);
        }

        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(entityService.getAllSitesCount());
        totalStatistics.setPages(entityService.getAllPagesCount());
        totalStatistics.setLemmas(entityService.getAllLemmasCount());
        totalStatistics.setIndexing(indexingStart);

        data.setTotal(totalStatistics);
        data.setDetailed(statisticsList);

        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    /**
     * Метод поиска.
     * @param query - поисковый запрос.
     * @param site - адрес сайта.
     * @param offset - указатель, на какой странице находится пользователь.
     * @param limit - количество совпадений на одной странице.
     * @return - список найденных совпадений.
     * @throws IOException
     */
    public List<RelevancePage> search(String query, String site, int offset, int limit) throws IOException {
        if (query.trim().isEmpty()) {
            return null;
        }
        List<Page> pages;
        if (site == null) {
            pages = entityService.getAllPages();
        } else {
            Site siteByUrl = entityService.getSiteByUrl(site);
            pages = entityService.getPagesBySite(siteByUrl);
        }

        List<Lemma> lemmasList = getLemmasListForSearching(query, pages);

        sortLemmasByFrequency(lemmasList);

        List<RelevancePage> relevancePages = getRelevancePages(lemmasList, pages, query);

        sortPagesByRelevance(relevancePages);

        return relevancePages.stream()
                .filter(page -> relevancePages.indexOf(page) >= offset * limit)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public boolean isIndexingStart() {
        return indexingStart;
    }

    /**
     * Метод, вычисляющий леммы на одной странице.
     * @param code - код страницы.
     * @return - список лемм.
     */
    public HashMap<String, Integer> getLemmasList(String code) {
        String text = lemmatisator.clearFromTags(code);
        return lemmatisator.splitTextInToLemmas(text);
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

    /**
     * Метод, возвращающий заголовок страницы.
     * @param html - код страницы.
     * @return - заголовок страницы.
     */
    public String getTitle(String html) {
        return Jsoup.parse(html).title();
    }

    /**
     * Метод, возвращающий родительский url страницы.
     * @param url - адрес страницы.
     * @return - адрес родительского сайта.
     */
    public String getParentUrl(String url) {
        return entityService.getParentUrl(url);
    }

    /**
     * Метод, возвращающий сниппет с найденным текстовым элементом.
     * @param path - адрес страницы.
     * @param query - поисковый запрос.
     * @return - сниппет.
     * @throws IOException
     */
    public String getSnippet(String path, String query) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        String snippet = "";
        String newQuery = getNewQuery(entityService.getPageByPath(path).getContent(), query);
        Connection connection = Jsoup.connect(entityService.getFullAddressByUri(path));
        Document doc = connection.get();
        Elements elements = doc.body().select("*");
        for (Element element : elements) {
            String text = element.ownText();
            if (text.trim().contains(newQuery)) {
                if (text.indexOf(newQuery) - 30 < 0 && text.indexOf(newQuery) + newQuery.length() + 30 > text.length()) {
                    snippet = text;
                }
                if (text.indexOf(newQuery) - 30 >= 0 && text.indexOf(newQuery) + newQuery.length() + 30 > text.length()) {
                    snippet = text.substring(text.indexOf(newQuery) - 30);
                }
                if (text.indexOf(newQuery) - 30 >= 0 && text.indexOf(newQuery) + newQuery.length() + 30 < text.length()) {
                    snippet = text.substring(text.indexOf(newQuery) - 30, text.indexOf(newQuery) + newQuery.length() + 30);
                }
                if (text.indexOf(newQuery) - 30 < 0 && text.indexOf(newQuery) + newQuery.length() + 30 < text.length()) {
                    snippet = text.substring(0, text.indexOf(newQuery) + newQuery.length() + 30);
                }
            }
        }
        stringBuilder.append("...").append(snippet, 0, snippet.indexOf(newQuery));
        stringBuilder.append("<b>").append(newQuery).append("</b>");
        stringBuilder.append(snippet.substring(snippet.indexOf(newQuery) + newQuery.length())).append("...");
        return stringBuilder.toString();
    }

    /**
     * Метод, для расчета абсолютной релевантность страницы.
     * @param page - сущность страницы.
     * @return - релевантность.
     */
    public float getRelevance(Page page) {
        float relevance = 0;
        for (SearchingIndex index : entityService.getLemmasByPage(page)) {
            relevance += index.getLemmasCount();
        }
        return relevance;
    }

    /**
     * Метод, сортирующий леммы по частоте.
     * @param lemmasList - список лемм.
     */
    public void sortLemmasByFrequency(List<Lemma> lemmasList) {
        lemmasList.sort(new Comparator<Lemma>() {
            @Override
            public int compare(Lemma o1, Lemma o2) {
                return o1.getFrequency() - o2.getFrequency();
            }
        });
    }

    /**
     * Метод, сортирующий страницы по релевантности.
     * @param relevancePages - список страниц.
     */
    public void sortPagesByRelevance(List<RelevancePage> relevancePages) {
        relevancePages.sort(new Comparator<RelevancePage>() {
            @Override
            public int compare(RelevancePage o1, RelevancePage o2) {
                return (int)(o2.getRelevance() - o1.getRelevance());
            }
        });
    }

    /**
     * Метод, создающий новый объект с результатами поиска.
     * @param path - адрес страницы.
     * @param title - заголовок страницы.
     * @param snippet - сниппет с совпадениями.
     * @param relevance - релевантность.
     * @return - новый объект с результатами поиска.
     */
    public RelevancePage getNewRelevancePage(String path, String title, String snippet, float relevance) {
        RelevancePage relevancePage = new RelevancePage();
        relevancePage.setUri(path);
        relevancePage.setTitle(title);
        relevancePage.setSnippet(snippet);
        relevancePage.setRelevance(relevance);

        return relevancePage;
    }

    /**
     * Метод, составляющий список лемм для поиска (с частотой не более 90 %).
     * @param query - поисковый запрос.
     * @param pages - список страниц.
     * @return - список лемм.
     */
    public List<Lemma> getLemmasListForSearching(String query, List<Page> pages) {
        HashMap<String, Integer> lemmas = lemmatisator.splitTextInToLemmas(query);
        List<Lemma> lemmasList = new ArrayList<>();

        int pagesCount = pages.size();
        double maximumPercentage = pagesCount - (10 * pagesCount) / 100.0;

        for (Map.Entry<String, Integer> map : lemmas.entrySet()) {
            if (entityService.getLemmasByLemma(map.getKey()).size() == 0) {
                continue;
            }
            Lemma lemma = entityService.getLemmaByLemma(map.getKey());
            if (lemma.getFrequency() < maximumPercentage) {
                lemmasList.add(lemma);
            }
        }
        return lemmasList;
    }

    /**
     * Метод, создающий список страниц с результатами поиска для вывода в интерфейс.
     * @param lemmasList - список лемм.
     * @param pages - список страниц.
     * @param query - поисковый запрос.
     * @return - список объектов с результатами поиска.
     * @throws IOException
     */
    public List<RelevancePage> getRelevancePages(List<Lemma> lemmasList, List<Page> pages, String query) throws IOException {
        List<RelevancePage> relevancePages = new ArrayList<>();
        if (lemmasList.size() == 1) {
            List<SearchingIndex> indexesByLemma = entityService.getIndexesByLemmaAndPages(lemmasList.get(0), pages);
            List<Page> pagesList = indexesByLemma.stream().map(SearchingIndex::getPage).toList();
            for (Page page : pagesList) {
                if (page.getContent().contains(getNewQuery(page.getContent(), query))) {
                    relevancePages.add(getNewRelevancePage(page.getPath(),getTitle(page.getContent())
                            ,getSnippet(page.getPath(), query), getRelevance(page)));
                }
            }
        }

        for (int i = 0; i < lemmasList.size() - 1; i++) {
            List<SearchingIndex> indexesByLemma = entityService.getIndexesByLemmaAndPages(lemmasList.get(i), pages);
            List<SearchingIndex> indexesByNextLemma = entityService.getIndexesByLemmaAndPages(lemmasList.get(i + 1), pages);
            List<Page> pagesList = indexesByLemma.stream().map(SearchingIndex::getPage).toList();
            for (SearchingIndex index : indexesByNextLemma) {
                for (Page page : pagesList) {
                    if (index.getPage().equals(page)
                            && page.getContent().contains(getNewQuery(page.getContent(), query))
                            && pages.contains(page)
                            && !isRelevancePageExist(relevancePages, getSnippet(page.getPath(), query))) {
                        relevancePages.add(getNewRelevancePage(page.getPath(),getTitle(page.getContent())
                                ,getSnippet(page.getPath(), query), getRelevance(page)));
                    }
                }
            }
        }
        return  relevancePages;
    }

    /**
     * Метод, создающий новый поисковый запрос с однокоренными словами из основного запроса и ищущий его в тексте страницы.
     * @param text - текст страницы.
     * @param query - поисковый запрос.
     * @return - новый поисковый запрос с однокоренными словами.
     */
    public String getNewQuery(String text, String query) {
        StringBuilder builder = new StringBuilder();
        String[] words = query.split("\\s+");
        String textWithoutTags = lemmatisator.clearFromTags(text);

        if (words.length == 1) {
            return lemmatisator.getCognateWord(textWithoutTags, words[0]);
        } else {
            for (String word : words) {
                builder.append(lemmatisator.getCognateWord(textWithoutTags, word)).append(" ");
            }
        }
        return builder.toString().trim();
    }

    /**
     * Метод, проверяющий существует ли в списке для вывода результатов страница по сниппету.
     * @param pages - список страниц для вывода.
     * @param snippet - сниппет, по которому происходит поиск.
     * @return - true - если страница существует.
     */
    public boolean isRelevancePageExist(List<RelevancePage> pages, String snippet) {
        return pages.stream().anyMatch(page -> page.getSnippet().equals(snippet));
    }

    /**
     * Метод, возвращающий список страниц для вывода в интерфейс (с полями site и siteName).
     * @param pages - список страниц из метода search.
     * @return - список страниц для корректного вывода в интерфейсе приложения при поиске.
     */
    public List<RelevancePageForResponse> getPagesForResponse(List<RelevancePage> pages) {
        List<RelevancePageForResponse> pagesForResponse = new ArrayList<>();
        for (RelevancePage page : pages) {
            RelevancePageForResponse pageForResponse = new RelevancePageForResponse();
            pageForResponse.setSite(entityService.getPageByPath(page.getUri()).getSite().getUrl());
            pageForResponse.setSiteName(entityService.getPageByPath(page.getUri()).getSite().getName());
            pageForResponse.setUri(page.getUri());
            pageForResponse.setTitle(page.getTitle());
            pageForResponse.setSnippet(page.getSnippet());
            pageForResponse.setRelevance(page.getRelevance());

            pagesForResponse.add(pageForResponse);
        }

        return pagesForResponse;
    }

    /**
     * Метод, проверяющий было ли приложение остановлено во время индексации и,
     * если да, добавляющий эти данные в таблицу site.
     */
    public void checkAppOffError() {
        for (Site site : entityService.getAllSites()) {
            if (site.getStatus().equals(Status.INDEXING) && !indexingStart) {
                site.setStatus(Status.FAILED);
                site.setLastError("Работа приложения была прервана во время индексации");
                site.setStatusTime(LocalDateTime.now());

                entityService.saveSite(site);
            }
        }
    }
}
