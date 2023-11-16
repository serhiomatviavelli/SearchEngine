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
import searchengine.util.EntityService;
import searchengine.util.Lemmatisator;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SearchServiceImpl implements SearchService {

    private final EntityService entityService;
    private final Lemmatisator lemmatisator;

    public SearchServiceImpl(EntityService entityService, Lemmatisator lemmatisator) {
        this.entityService = entityService;
        this.lemmatisator = lemmatisator;
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
    @Override
    public List<RelevancePage> search(String query, String site, int offset, int limit) {
        if (query.trim().isEmpty()) {
            return null;
        }

        List<Page> pages;
        if (site == null) {
            Lemma firstWordInQueryLemma = entityService.getLemmaByLemma(lemmatisator.getLemma(query.split("\\s+")[0]));
            List<SearchingIndex> indexes = entityService.getIndexesByLemma(firstWordInQueryLemma);
            pages = indexes.stream().map(SearchingIndex::getPage).toList();
        } else {
            Site siteByUrl = entityService.getSiteByUrl(site);
            pages = entityService.getPagesBySite(siteByUrl);
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
     * @param path - адрес страницы.
     * @param query - поисковый запрос.
     * @return - сниппет.
     * @throws IOException
     */
    public String getSnippet(String path, String query) throws IOException {
        StringBuilder builder = new StringBuilder();
        String newQuery = getNewQuery(entityService.getPageByPath(path).getContent(), query).toLowerCase();
        Connection connection = Jsoup.connect(entityService.getFullAddressByUri(path));
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
     * @return - список лемм.
     */
    public List<Lemma> getLemmasListForSearching(String query) {
        HashMap<String, Integer> lemmas = lemmatisator.splitTextInToLemmas(query);
        List<Lemma> lemmasList = new ArrayList<>();

        long pagesCount = entityService.getAllPagesCount();
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
            for (Page page : pages) {
                if (page.getContent().toLowerCase().contains(getNewQuery(page.getContent(), query).toLowerCase())) {
                    relevancePages.add(getNewRelevancePage(page.getPath(),getTitle(page.getContent())
                            ,getSnippet(page.getPath(), query), getRelevance(page)));
                }
            }
        } else {
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
                            relevancePages.add(getNewRelevancePage(page.getPath(), getTitle(page.getContent())
                                    , getSnippet(page.getPath(), query), getRelevance(page)));
                        }
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
    @Override
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


}
