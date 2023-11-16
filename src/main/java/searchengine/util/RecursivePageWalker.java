package searchengine.util;

import org.springframework.stereotype.Component;
import searchengine.services.IndexingService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveAction;

@Component
public class RecursivePageWalker extends RecursiveAction {

    private final WebSiteTree webSiteTree;

    private final IndexingService indexingService;

    private static final CopyOnWriteArrayList<String> linksPool = new CopyOnWriteArrayList();

    public RecursivePageWalker(WebSiteTree webSiteTree, IndexingService indexingService) {
        this.webSiteTree = webSiteTree;
        this.indexingService = indexingService;
    }

    @Override
    protected void compute() {
        linksPool.add(webSiteTree.getUrl());
        ConcurrentSkipListSet<String> links = HtmlParser.getLinks(webSiteTree.getUrl());

        for (String link : links) {
            if (!linksPool.contains(link)) {
                linksPool.add(link);
                webSiteTree.addChildren(new WebSiteTree(link));
            }
        }

        List<RecursivePageWalker> recursivePageWalkerList = new ArrayList<>();

        for (WebSiteTree child : webSiteTree.getChildren()) {
            try {
                indexingService.indexPage(child.getUrl());
                RecursivePageWalker recursivePageWalker = new RecursivePageWalker(child, indexingService);
                recursivePageWalker.fork();
                recursivePageWalkerList.add(recursivePageWalker);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        for (RecursivePageWalker recursivePageWalker : recursivePageWalkerList) {
            recursivePageWalker.join();
        }
    }
}
