package searchengine.services;

public interface IndexingService {

    void startIndexing();

    boolean isIndexingStart();

    void stopIndexing();

    void indexPage(String url);

    String getParentUrl(String path);

    String getFullAddressByUri(String uri);
}
