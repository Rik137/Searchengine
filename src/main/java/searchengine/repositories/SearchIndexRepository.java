package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SearchIndexEntity;

import java.util.List;

@Repository
public interface SearchIndexRepository extends JpaRepository<SearchIndexEntity, Integer> {
    List<SearchIndexEntity> findByPageId(int pageId);
    List<SearchIndexEntity> findByLemmaId(int lemmaId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SearchIndexEntity si WHERE si.page.id = :pageId")
    void deleteByPageId(@Param("pageId") int pageId);
}