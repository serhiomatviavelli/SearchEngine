package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.dto.result.RelevancePage;
import searchengine.dto.result.RelevancePageForResponse;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;
import searchengine.model.entity.Site;
import searchengine.model.repository.LemmaRepository;
import searchengine.model.repository.PageRepository;
import searchengine.model.repository.SearchingIndexRepository;
import searchengine.model.repository.SiteRepository;
import searchengine.util.Lemmatisator;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchServiceImpl implements SearchService {

    private final Lemmatisator lemmatisator;
    private final SearchingIndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingService indexingService;

    public SearchServiceImpl(Lemmatisator lemmatisator, SearchingIndexRepository indexRepository,
                             LemmaRepository lemmaRepository, PageRepository pageRepository,
                             SiteRepository siteRepository, IndexingService indexingService) {
        this.lemmatisator = lemmatisator;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.indexingService = indexingService;
    }

    /**
     * Метод поиска.
     * @param query - поисковый запрос.
     * @param site - адрес сайта.
     * @param offset - указатель, на какой странице находится пользователь.
     * @param limit - количество совпадений на одной странице.
     * @return - список найденных совпадений.
     */
    @Override
    public List<RelevancePage> search(String query, String site, int offset, int limit) {
        if (query.trim().isEmpty()) {
            return null;
        }
        query = query.toLowerCase();
        List<Page> pages;
        String firstWordInQuery = lemmatisator.getLemma(query.split("\\s+")[0]);
        List<Lemma> firstWordInQueryLemmas = lemmaRepository.findByLemma(firstWordInQuery);
        if (firstWordInQueryLemmas.isEmpty()) {
            return new ArrayList<>();
        }
        Lemma firstWordInQueryLemma = firstWordInQueryLemmas.get(0);
        List<SearchingIndex> indexes = indexRepository.findByLemma(firstWordInQueryLemma);
        if (site == null) {
            pages = indexes.stream().map(SearchingIndex::getPage).toList();
        } else {
            Site siteByUrl = siteRepository.findByUrl(site).get(0);
            pages = indexes.stream().map(SearchingIndex::getPage).filter(page -> page.getSite().equals(siteByUrl)).toList();
        }
        List<Lemma> lemmasList = getLemmasListForSearching(query);
        sortLemmasByFrequency(lemmasList);
        List<RelevancePage> relevancePages = null;
        try {
            relevancePages = getRelevancePages(lemmasList, pages, query);
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert relevancePages != null;
        sortPagesByRelevance(relevancePages);
        return relevancePages;
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
     * Метод, возвращающий сниппет с найденным текстовым элементом.
     * @param page - страница.
     * @param query - поисковый запрос.
     * @return - сниппет.
     * @throws IOException
     */
    public String getSnippet(Page page, String query) throws IOException {
        StringBuilder builder = new StringBuilder();
        String newQuery = getNewQuery(page.getContent(), query).toLowerCase();
        Connection connection = Jsoup.connect(indexingService.getFullAddressByUri(page.getPath()));
        Document doc = connection.get();
        Elements elements = doc.body().select("*");
        for (Element element : elements) {
            String text = element.ownText().toLowerCase();
            Pattern pattern = Pattern.compile(".{0,30}" + newQuery + ".{0,30}");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                int firstMatchIndex = matcher.group().indexOf(newQuery);
                builder.append("...")
                        .append(matcher.group().substring(0, firstMatchIndex))
                        .append("<b>").append(newQuery).append("</b>")
                        .append(matcher.group().substring(firstMatchIndex + newQuery.length()))
                        .append("...");
                return builder.toString();
            }
        }
        return "";
    }

    /**
     * Метод, для расчета абсолютной релевантность страницы.
     * @param page - сущность страницы.
     * @return - релевантность.
     */
    public float getRelevance(Page page) {
        float relevance = 0;
        for (SearchingIndex index : indexRepository.findByPage(page)) {
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
     * @return - список лемм.
     */
    public List<Lemma> getLemmasListForSearching(String query) {
        HashMap<String, Integer> lemmas = lemmatisator.splitTextInToLemmas(query);
        List<Lemma> lemmasList = new ArrayList<>();

        long pagesCount = pageRepository.count();
        double maximumPercentage = pagesCount - (10 * pagesCount) / 100.0;

        for (Map.Entry<String, Integer> map : lemmas.entrySet()) {
            if (lemmaRepository.findByLemma(map.getKey()).size() == 0) {
                continue;
            }
            Lemma lemma = lemmaRepository.findByLemma(map.getKey()).get(0);
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
            pages.forEach(page -> {
                try {
                    relevancePages.add(getNewRelevancePage(page.getPath(),getTitle(page.getContent())
                                    ,getSnippet(page, query), getRelevance(page)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } else {
            for (Page page : pages) {
                String newQuery = getNewQuery(lemmatisator.clearFromTags(page.getContent()), query);
                if (page.getContent().toLowerCase().contains(newQuery) &&
                        pages.contains(page)) {
                    relevancePages.add(getNewRelevancePage(page.getPath(), getTitle(page.getContent())
                                    , getSnippet(page, query), getRelevance(page)));
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
     * Метод, возвращающий список страниц для вывода в интерфейс (с полями site и siteName).
     * @param pages - список страниц из метода search.
     * @return - список страниц для корректного вывода в интерфейсе приложения при поиске.
     */
    @Override
    public List<RelevancePageForResponse> getPagesForResponse(List<RelevancePage> pages) {
        List<RelevancePageForResponse> pagesForResponse = new ArrayList<>();
        for (RelevancePage page : pages) {
            RelevancePageForResponse pageForResponse = new RelevancePageForResponse();
            Page pageByPath = pageRepository.findByPath(page.getUri()).get(0);
            pageForResponse.setSite(pageByPath.getSite().getUrl());
            pageForResponse.setSiteName(pageByPath.getSite().getName());
            pageForResponse.setUri(page.getUri());
            pageForResponse.setTitle(page.getTitle());
            pageForResponse.setSnippet(page.getSnippet());
            pageForResponse.setRelevance(page.getRelevance());

            pagesForResponse.add(pageForResponse);
        }

        return pagesForResponse;
    }
}
