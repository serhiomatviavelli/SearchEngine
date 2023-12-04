package searchengine.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import searchengine.model.Status;
import searchengine.model.entity.Site;
import searchengine.model.repository.SiteRepository;

import java.time.LocalDateTime;

@AllArgsConstructor
@Configuration
public class AppConfig {

    private SiteRepository siteRepository;

    @Bean
    public void checkAppOffError() {
        for (Site site : siteRepository.findAll()) {
            if (site.getStatus().equals(Status.INDEXING)) {
                site.setStatus(Status.FAILED);
                site.setLastError("Работа приложения была прервана во время индексации");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        }
    }
}
