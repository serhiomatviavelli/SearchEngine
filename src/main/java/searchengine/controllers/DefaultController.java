package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.services.SiteService;

@Controller
public class DefaultController {

    private final SiteService siteService;

    public DefaultController(SiteService siteService) {
        this.siteService = siteService;
    }

    /**
     * Метод формирует страницу из HTML-файла index.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */
    @RequestMapping("/")
    public String index() {
        siteService.checkAppOffError();
        return "index";
    }
}
