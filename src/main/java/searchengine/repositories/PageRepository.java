package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageRepository extends CrudRepository<PageEntity, Integer> {
    boolean existsByPathAndSite(String path, SiteEntity site);
    void deleteBySite(SiteEntity site);
    int countBySite(SiteEntity site);
}
