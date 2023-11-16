package searchengine.services;

import searchengine.dto.result.RelevancePage;
import searchengine.dto.result.RelevancePageForResponse;

import java.util.List;

public interface SearchService {

    List<RelevancePage> search(String query, String site, int offset, int limit);

    List<RelevancePageForResponse> getPagesForResponse(List<RelevancePage> pages);

}
