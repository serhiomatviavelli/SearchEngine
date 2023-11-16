package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;
import searchengine.model.entity.Site;
import searchengine.util.EntityService;
import searchengine.util.Lemmatisator;
import searchengine.util.RecursivePageWalker;
import searchengine.util.WebSiteTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final EntityService entityService;
    private final SitesList sitesList;
    private final Lemmatisator lemmatisator;

    private final AtomicBoolean indexingStart = new AtomicBoolean(false);
    private final AtomicBoolean indexingStop = new AtomicBoolean(false);

    public IndexingServiceImpl(EntityService entityService, SitesList sitesList, Lemmatisator lemmatisator) {
        this.entityService = entityService;
        this.sitesList = sitesList;
        this.lemmatisator = lemmatisator;
    }

    /**
     * Метод, начинающий индексацию сайтов.
     */
    @Override
    public void startIndexing() {
        indexingStart.set(true);

        entityService.deleteAllData();

        entityService.addIndexingSites(sitesList);

        for (Site site : entityService.getAllSites()) {
            try {
                WebSiteTree webSiteTree = new WebSiteTree(site.getUrl());
                RecursivePageWalker recursivePageWalker = new RecursivePageWalker(webSiteTree, this);
                ForkJoinPool forkJoinPool = new ForkJoinPool();
                forkJoinPool.invoke(recursivePageWalker);
                if (indexingStop.get()) {
                    forkJoinPool.shutdownNow();
                    entityService.stopIndexingInfoAdd();
                    indexingStop.set(false);
                    break;
                } else {
                    entityService.saveIndexedSiteInfo(site);
                }
                entityService.saveIndexedSiteInfo(site);
            } catch (Exception e) {
                entityService.saveFailedIndexingSiteInfo(site, e.getMessage());
            }
        }
        indexingStart.set(false);
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
        String parentUrl = entityService.getParentUrl(url);
        String path = url.substring(url.indexOf(parentUrl) + parentUrl.length());

        List<Page> pagesByUrl = entityService.getPagesByPath(path);
        if (pagesByUrl.size() != 0 &&
                entityService.getIndexesCountByPage(pagesByUrl.get(0)) != 0) {
            try {
                updatePage(entityService.getPageByPath(path));
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
            Site site = entityService.getSiteByUrl(getParentUrl(url));
            Page page = entityService.saveNewPage(site, url, response.statusCode(), response.body());
            String text = lemmatisator.clearFromTags(response.body());
            HashMap<String, Integer> lemmas = lemmatisator.getLemmasList(text);
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

    /**
     * Метод, обновления существующей страницы.
     * @param page - страница, которую необходимо обновить.
     */
    public void updatePage(Page page) throws IOException {
        String fullAddress = entityService.getFullAddressByUri(page.getPath());
        List<SearchingIndex> indexes = entityService.getIndexesByPage(page);
        for (SearchingIndex index : indexes) {
            Lemma lemma = index.getLemma();
            List<Lemma> lemmasWithOneFrequency = new ArrayList<>();
            if (lemma.getFrequency() == 1) {
                lemmasWithOneFrequency.add(lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() - 1);
                entityService.saveLemma(lemma);
            }
            entityService.deleteIndex(index);
            for (Lemma lemmaWithOneFrequency : lemmasWithOneFrequency) {
                entityService.deleteLemma(lemmaWithOneFrequency);
            }
        }
        entityService.deletePage(page);

        indexingPage(fullAddress);
    }

    @Override
    public boolean isIndexingStart() {
        return indexingStart.get();
    }

    /**
     * Метод, возвращающий родительский url страницы.
     * @param url - адрес страницы.
     * @return - адрес родительского сайта.
     */
    @Override
    public String getParentUrl(String url) {
        return entityService.getParentUrl(url);
    }

}
