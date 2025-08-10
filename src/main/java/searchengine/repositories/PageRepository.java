package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

public interface PageRepository extends CrudRepository<PageEntity, Integer> {
    boolean existsByPathAndSiteId(String path, Integer siteId);
    void deleteBySiteId(Integer siteId);
    int countBySiteId(Integer siteId);
    Optional<PageEntity> findBySiteIdAndPath(Integer siteId, String path);

}

