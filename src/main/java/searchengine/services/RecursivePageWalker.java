package searchengine.services;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveAction;

@Service
public class RecursivePageWalker extends RecursiveAction {

    private final WebSiteTree webSiteTree;

    private final SiteService siteService;

    private static CopyOnWriteArrayList<String> linksPool = new CopyOnWriteArrayList();

    public RecursivePageWalker(WebSiteTree webSiteTree, SiteService siteService) {
        this.webSiteTree = webSiteTree;
        this.siteService = siteService;
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
                siteService.indexPage(child.getUrl());
                RecursivePageWalker recursivePageWalker = new RecursivePageWalker(child, siteService);
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
