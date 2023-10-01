package searchengine.dto.response;

import lombok.Data;

@Data
public class IndexingResponse {
    private boolean result;

    public IndexingResponse() {
        result = true;
    }
}
