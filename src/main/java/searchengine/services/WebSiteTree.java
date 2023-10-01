package searchengine.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Сервис, представляющий карту сайта.
 */
@Service
public class WebSiteTree {

    private String url;

    private CopyOnWriteArrayList<WebSiteTree> children;

    public CopyOnWriteArrayList<WebSiteTree> getChildren() {
        return children;
    }

    public String getUrl() {
        return url;
    }

    public WebSiteTree(String url) {
        this.url = url;
        children = new CopyOnWriteArrayList<>();
    }

    public WebSiteTree() {}

    public void addChildren(WebSiteTree child) {
        children.add(child);
    }
}
