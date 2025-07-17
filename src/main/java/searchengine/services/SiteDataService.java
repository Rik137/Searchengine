package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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
            pageRepository.deleteBySite(old);
            siteRepository.delete(old);
        }
    }
}
