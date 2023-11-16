package searchengine.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import searchengine.model.Status;
import searchengine.model.entity.Site;
import searchengine.util.EntityService;

import java.time.LocalDateTime;

@AllArgsConstructor
@Configuration
public class AppConfig {

    private EntityService entityService;

    @Bean
    public void checkAppOffError() {
        for (Site site : entityService.getAllSites()) {
            if (site.getStatus().equals(Status.INDEXING)) {
                site.setStatus(Status.FAILED);
                site.setLastError("Работа приложения была прервана во время индексации");
                site.setStatusTime(LocalDateTime.now());
                entityService.saveSite(site);
            }
        }
    }
}
