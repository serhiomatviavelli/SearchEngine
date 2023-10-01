package searchengine.dto.result;

import lombok.Data;

@Data
public class RelevancePage {

    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
