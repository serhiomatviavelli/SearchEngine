package searchengine.dto.statistics;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class TotalStatistics {
    private int sites;
    private int pages;
    private int lemmas;
    private boolean indexing;
}
