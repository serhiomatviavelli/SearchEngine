package searchengine.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IndexingFailedResponse extends IndexingResponse {
    String error;

    public IndexingFailedResponse(String error) {
        setResult(false);
        this.error = error;
    }
}
