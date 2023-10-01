package searchengine.dto.response;

import lombok.Data;
import searchengine.dto.result.RelevancePageForResponse;

import java.util.List;

@Data
public class SearchSuccessResponse {
    private boolean result;
    private int count;
    private List<RelevancePageForResponse> data;

    public SearchSuccessResponse(boolean result, int count, List<RelevancePageForResponse> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}
