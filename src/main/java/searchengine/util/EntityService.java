package searchengine.util;

import org.springframework.stereotype.Component;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;
import searchengine.model.entity.Site;
import searchengine.model.repository.LemmaRepository;
import searchengine.model.repository.PageRepository;
import searchengine.model.repository.SearchingIndexRepository;
import searchengine.model.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс, обрабатывающий запросы к БД.
 */
@Component
public class EntityService {

    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    private final LemmaRepository lemmaRepository;

    private final SearchingIndexRepository indexRepository;

    public EntityService(SiteRepository siteRepository, PageRepository pageRepository,
                         LemmaRepository lemmaRepository, SearchingIndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public void saveSite(Site site) {
        siteRepository.save(site);
    }

    /**
     * Метод, меняющий дату последнего обновления сайта в таблице site.
     * @param site - сайт, дату обновления которого необходимо изменить.
     */
    public void saveSiteDate(Site site) {
        site.setStatusTime(LocalDateTime.now());
        saveSite(site);
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
                saveSite(site);
            }
        }
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
            saveSite(site);
        }
    }

    public Site getSiteByUrl(String url) {
        return siteRepository.findByUrl(url).get(0);
    }

    public List<Site> getAllSites() {
        return siteRepository.findAll();
    }

    public int getAllSitesCount() {
        return getAllSites().size();
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
     * Метод, добавляющий информацию о сайте в случае ошибки индексации.
     * @param site - сайт, при индексации которого произошла ошибка.
     * @param reason - причина ошибки.
     */
    public void saveFailedIndexingSiteInfo(Site site, String reason) {
        site.setStatus(Status.FAILED);
        site.setLastError(reason);
        saveSiteDate(site);
    }

    public int getPagesCountBySite(Site site) {
        return pageRepository.findBySite(site).size();
    }

    public long getAllPagesCount() {
        return pageRepository.count();
    }

    public List<Page> getPagesByPath(String path) {
        return pageRepository.findByPath(path);
    }

    public Page getPageByPath(String path) {
        return getPagesByPath(path).get(0);
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

    public List<Page> getPagesBySite(Site site) {
        return pageRepository.findBySite(site);
    }

    public List<Lemma> getLemmasByLemma(String lemma) {
        return lemmaRepository.findByLemma(lemma);
    }

    public Lemma getLemmaByLemma(String lemma) {
        return getLemmasByLemma(lemma).get(0);
    }

    public int getLemmasCountBySite(Site site) {
        return lemmaRepository.findBySite(site).size();
    }

    public List<Lemma> getAllLemmas() {
        return lemmaRepository.findAll();
    }

    public int getAllLemmasCount() {
        return getAllLemmas().size();
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

    public void saveLemma(Lemma lemma) {
        lemmaRepository.save(lemma);
    }

    public List<SearchingIndex> getIndexesByPage(Page page) {
        return indexRepository.findByPage(page);
    }

    public int getIndexesCountByPage(Page page) {
        return getIndexesByPage(page).size();
    }

    public List<SearchingIndex> getLemmasByPage(Page page) {
        return indexRepository.findByPage(page);
    }

    public List<SearchingIndex> getIndexesByLemma(Lemma lemma) {
        return indexRepository.findByLemma(lemma);
    }

    /**
     * Метод, возвращающий список индексов по лемме и страницам.
     * @param lemma - лемма.
     * @param pages - список страниц, на которых необходимо найти лемму.
     * @return - список нужных индексов.
     */
    public List<SearchingIndex> getIndexesByLemmaAndPages(Lemma lemma, List<Page> pages) {
        List<SearchingIndex> indexes = new ArrayList<>();
        for (SearchingIndex index : getIndexesByLemma(lemma)) {
            for (Page page : pages) {
                if (index.getPage().equals(page)) {
                    indexes.add(index);
                }
            }
        }
        return indexes;
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

    public void deleteIndex(SearchingIndex index) {
        indexRepository.delete(index);
    }

    public void deleteLemma(Lemma lemma) {
        lemmaRepository.delete(lemma);
    }

    public void deletePage(Page page) {
        pageRepository.delete(page);
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
     * Метод, возвращающий родительский url страницы из БД.
     * @param url - адрес страницы.
     * @return - сайт, которому эта страница принадлежит.
     */
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
    public String getFullAddressByUri(String uri) {
        StringBuilder builder = new StringBuilder();
        Site site = getPageByPath(uri).getSite();
        builder.append(site.getUrl());
        builder.append(uri);
        return builder.toString();
    }
}
