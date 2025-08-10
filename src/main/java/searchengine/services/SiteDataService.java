package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
@Slf4j
@Service
public class SiteDataService {

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Transactional
    public void deleteOldSiteData(String url) {
        SiteEntity old = siteRepository.findByUrl(url);
        if (old != null) {
            pageRepository.deleteBySiteId(old.getId());
            siteRepository.delete(old);
            log.info("Удалены старые данные сайта {}", url);
        }
    }
}
