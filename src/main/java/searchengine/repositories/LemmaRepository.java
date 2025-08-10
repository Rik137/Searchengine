package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    List<LemmaEntity> findBySiteId(int siteId);
    List<LemmaEntity> findAllBySite(SiteEntity site);
    LemmaEntity findByLemmaAndSiteId(String lemma, int siteId);
    Optional<LemmaEntity> findByLemmaAndSite(String lemma, SiteEntity site);

}
