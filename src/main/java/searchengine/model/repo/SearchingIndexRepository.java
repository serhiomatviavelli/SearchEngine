package searchengine.model.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.entity.Lemma;
import searchengine.model.entity.Page;
import searchengine.model.entity.SearchingIndex;

import java.util.List;

@Repository
public interface SearchingIndexRepository extends JpaRepository<SearchingIndex, Integer> {

    List<SearchingIndex> findByPage(Page page);

    List<SearchingIndex> findByLemma(Lemma lemma);

}