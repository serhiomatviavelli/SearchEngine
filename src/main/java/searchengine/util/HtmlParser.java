package searchengine.util;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentSkipListSet;

import static java.lang.Thread.sleep;

/**
 * Сервис, производящий парсинг html-кода страницы.
 */
@Component
public class HtmlParser {

    private static ConcurrentSkipListSet<String> links;

    /**
     * Метод, проверяющий является ли строка ссылкой.
     * @param link - строка, которую необходимо проверить.
     * @return - true, в случае если строка является ссылкой.
     */
    private static boolean isLink(String link) {
        String regex = "http[s]?://[^#,\\s]*\\.?[a-z]*\\.ru[^#,\\s]*";
        return link.matches(regex);
    }

    /**
     * Метод, возвращающий список ссылок на другие страницы, найденные на заданной.
     * @param url - адрес страницы.
     * @return - список ссылок, найденных на этой странице.
     */
    public static ConcurrentSkipListSet<String> getLinks(String url) {
        links = new ConcurrentSkipListSet<>();
        try {
            sleep(150);
            Connection connection = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .followRedirects(false);
            Document document = connection.get();
            Elements elements = document.select("body").select("a");
            for (Element element : elements) {
                String link = element.absUrl("href");
                if (isLink(link) && !isFile(link)) {
                    links.add(link);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return links;
    }

    /**
     * Метод, проверяющий является ли ссылка файлом.
     * @param link - адрес ссылки.
     * @return - true - если ссылка является файлом.
     */
    private static boolean isFile(String link) {
        link.toLowerCase();
        return link.contains(".jpg")
                || link.contains(".jpeg")
                || link.contains(".png")
                || link.contains(".gif")
                || link.contains(".webp")
                || link.contains(".pdf")
                || link.contains(".eps")
                || link.contains(".xlsx")
                || link.contains(".doc")
                || link.contains(".pptx")
                || link.contains(".docx")
                || link.contains("?_ga");
    }
}
