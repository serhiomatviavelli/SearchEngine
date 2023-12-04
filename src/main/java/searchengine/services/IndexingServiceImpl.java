package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.Status;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;
import searchengine.model.entity.Site;
import searchengine.model.repository.LemmaRepository;
import searchengine.model.repository.PageRepository;
import searchengine.model.repository.SearchingIndexRepository;
import searchengine.model.repository.SiteRepository;
import searchengine.util.Lemmatisator;
import searchengine.util.RecursivePageWalker;
import searchengine.util.WebSiteTree;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final Lemmatisator lemmatisator;
    private final SearchingIndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    private final AtomicBoolean indexingStart = new AtomicBoolean(false);
    private final AtomicBoolean indexingStop = new AtomicBoolean(false);

    public IndexingServiceImpl(SitesList sitesList, Lemmatisator lemmatisator,
                               SearchingIndexRepository indexRepository, LemmaRepository lemmaRepository,
                               PageRepository pageRepository, SiteRepository siteRepository) {
        this.sitesList = sitesList;
        this.lemmatisator = lemmatisator;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    /**
     * Метод, начинающий индексацию сайтов.
     */
    @Override
    public void startIndexing() {
        Runnable startIndexing = new Runnable() {
            @Override
            public void run() {
                deleteAllData();
                addIndexingSites(sitesList);
                siteRepository.findAll().parallelStream().forEach(site -> {
                    try {
                        WebSiteTree webSiteTree = new WebSiteTree(site.getUrl());
                        RecursivePageWalker recursivePageWalker = new RecursivePageWalker(webSiteTree, IndexingServiceImpl.this);
                        ForkJoinPool forkJoinPool = new ForkJoinPool();
                        forkJoinPool.invoke(recursivePageWalker);
                        if (indexingStop.get()) {
                            forkJoinPool.shutdownNow();
                            stopIndexingInfoAdd();
                            indexingStop.set(false);
                        } else {
                            saveIndexedSiteInfo(site);
                        }
                        saveIndexedSiteInfo(site);
                    } catch (Exception e) {
                        saveFailedIndexingSiteInfo(site, e.getMessage());
                    }
                });
                indexingStart.set(false);
            }
        };
        CompletableFuture.runAsync(startIndexing);
        indexingStart.set(true);
    }

    /**
     * Метод, завершающий индексацию сайтов.
     */
    @Override
    public void stopIndexing() {
        if (isIndexingStart()) {
            indexingStop.set(true);
        }
    }

    /**
     * Метод, индексирующий заданную страницу по url, либо обновляющий ее.
     * @param url - url страницы, которую необходимо проиндексировать.
     */
    @Override
    public void indexPage(String url) {
        String parentUrl = getParentUrl(url);
        String path = url.substring(url.indexOf(parentUrl) + parentUrl.length());

        List<Page> pagesByUrl = pageRepository.findByPath(path);
        if (pagesByUrl.size() != 0 &&
                indexRepository.findByPage(pagesByUrl.get(0)).size() != 0) {
            try {
                updatePage(pageRepository.findByPath(path).get(0));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                indexingPage(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Метод, индексирующий страницу.
     * @param url - адрес страницы, которую необходимо проиндексировать.
     * @throws IOException
     */
    public void indexingPage(String url) throws IOException {
        Connection.Response response = Jsoup.connect(url).execute();

        if (response.statusCode() == 200) {
            Site site = siteRepository.findByUrl(getParentUrl(url)).get(0);
            Page page = saveNewPage(site, url, response.statusCode(), response.body());
            String text = lemmatisator.clearFromTags(response.body());
            HashMap<String, Integer> lemmas = lemmatisator.getLemmasList(text);
            for (Map.Entry<String, Integer> map : lemmas.entrySet()) {
                List<Lemma> lemmaByLemma = lemmaRepository.findByLemma(map.getKey());
                if (!lemmaByLemma.isEmpty()) {
                    Lemma lemma = lemmaRepository.findByLemma(map.getKey()).get(0);
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    lemmaRepository.save(lemma);
                } else {
                    saveNewLemma(site, map.getKey());
                }
                saveNewIndex(page, lemmaRepository.findByLemma(map.getKey()).get(0), map.getValue().floatValue());
            }
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    /**
     * Метод, обновления существующей страницы.
     * @param page - страница, которую необходимо обновить.
     */
    public void updatePage(Page page) throws IOException {
        String fullAddress = getFullAddressByUri(page.getPath());
        List<SearchingIndex> indexes = indexRepository.findByPage(page);
        for (SearchingIndex index : indexes) {
            Lemma lemma = index.getLemma();
            List<Lemma> lemmasWithOneFrequency = new ArrayList<>();
            if (lemma.getFrequency() == 1) {
                lemmasWithOneFrequency.add(lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() - 1);
                lemmaRepository.save(lemma);
            }
            indexRepository.delete(index);
            for (Lemma lemmaWithOneFrequency : lemmasWithOneFrequency) {
                lemmaRepository.delete(lemmaWithOneFrequency);
            }
        }
        pageRepository.delete(page);

        indexingPage(fullAddress);
    }

    @Override
    public boolean isIndexingStart() {
        return indexingStart.get();
    }

    /**
     * Метод, возвращающий родительский url страницы из БД.
     * @param url - адрес страницы.
     * @return - сайт, которому эта страница принадлежит.
     */
    @Override
    public String getParentUrl(String url) {
        String regex = "(http[s]?://[^#,\\s]*\\.?[a-z]*\\.ru)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find() && siteRepository.findByUrl(matcher.group()).size() != 0) {
            return matcher.group();
        }
        return null;
    }

    /**
     * Метод, возвращающий полный адрес страницы.
     * @param uri - адрес страницы от корня сайта.
     * @return - полный адрес страницы.
     */
    @Override
    public String getFullAddressByUri(String uri) {
        StringBuilder builder = new StringBuilder();
        Site site = pageRepository.findByPath(uri).get(0).getSite();
        builder.append(site.getUrl());
        builder.append(uri);
        return builder.toString();
    }

    /**
     * Метод, удаляющий все данные из БД.
     */
    public void deleteAllData() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    /**
     * Метод, добавляющий в таблицу site сайты из файла properties.yaml.
     * @param sitesList - список сайтов из файла properties.yaml.
     */
    public void addIndexingSites(SitesList sitesList) {
        for (searchengine.config.Site siteFromProp : sitesList.getSites()) {
            Site site = new Site();
            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setUrl(siteFromProp.getUrl());
            site.setName(siteFromProp.getName());
            siteRepository.save(site);
        }
    }

    /**
     * Метод, сохраняющий информацию в таблицу site в случае остановки индексации пользователем.
     */
    public void stopIndexingInfoAdd() {
        for (Site site : siteRepository.findAll()) {
            if (!site.getStatus().equals(Status.INDEXED)) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("Индексация остановлена пользователем");
                siteRepository.save(site);
            }
        }
    }

    /**
     * Метод, сохраняющий информацию о проиндексированном сайте в таблицу site.
     * @param site - проиндексированный сайт.
     */
    public void saveIndexedSiteInfo(Site site) {
        site.setStatus(Status.INDEXED);
        saveSiteDate(site);
    }

    /**
     * Метод, меняющий дату последнего обновления сайта в таблице site.
     * @param site - сайт, дату обновления которого необходимо изменить.
     */
    public void saveSiteDate(Site site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    /**
     * Метод, добавляющий информацию о сайте в случае ошибки индексации.
     * @param site - сайт, при индексации которого произошла ошибка.
     * @param reason - причина ошибки.
     */
    public void saveFailedIndexingSiteInfo(Site site, String reason) {
        site.setStatus(Status.FAILED);
        site.setLastError(reason);
        saveSiteDate(site);
    }

    /**
     * Метод, сохраняющий новую страницу в таблицу page.
     * @param site - сайт, которому принадлежит страница.
     * @param url - адрес страницы.
     * @param code - код ответа, полученный при запросе.
     * @param content - html-код страницы.
     * @return - страница, добавленная в БД.
     */
    public Page saveNewPage(Site site, String url, Integer code, String content) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(url.substring(url.indexOf(getParentUrl(url)) + getParentUrl(url).length()));
        page.setCode(code);
        page.setContent(content);
        pageRepository.save(page);

        return page;
    }

    /**
     * Метод, добавляющий новую лемму в таблицу lemma.
     * @param site - сайт, на котором найдена лемма.
     * @param lemma - лемма.
     */
    public void saveNewLemma(Site site, String lemma) {
        Lemma newLemma = new Lemma();
        newLemma.setSite(site);
        newLemma.setLemma(lemma);
        newLemma.setFrequency(1);
        lemmaRepository.save(newLemma);
    }

    /**
     * метод, добавляющий новый индекс в таблицу index.
     * @param page - страница с леммой.
     * @param lemma - лемма, связанная со страницей.
     * @param lemmasCount - количество данной леммы для данной страницы.
     */
    public void saveNewIndex(Page page, Lemma lemma, Float lemmasCount) {
        SearchingIndex index = new SearchingIndex();
        index.setPage(page);
        index.setLemma(lemma);
        index.setLemmasCount(lemmasCount);
        indexRepository.save(index);
    }
}
