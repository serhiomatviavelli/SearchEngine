package searchengine.dto.result;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RelevancePageForResponse extends RelevancePage {
    private String site;
    private String siteName;
}
