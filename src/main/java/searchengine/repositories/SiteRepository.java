package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.SiteEntity;
import searchengine.model.StatusEntity;

import java.util.List;

public interface SiteRepository extends CrudRepository<SiteEntity, Integer> {
   SiteEntity findByUrl(String url);

   void delete(SiteEntity site);

   List<SiteEntity> findAllByStatus(StatusEntity status);
}
