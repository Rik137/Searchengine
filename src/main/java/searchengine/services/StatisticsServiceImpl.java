package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.serviceinterfaces.StatisticsService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Override
    public StatisticsResponse getStatistics() {

        List<Site> configSites = sitesList.getSites();

        // Берём сайты из БД, чтобы получить актуальный статус
        Iterable<SiteEntity> siteEntitiesIterable = siteRepository.findAll();
        List<SiteEntity> siteEntities = new ArrayList<>();
        siteEntitiesIterable.forEach(siteEntities::add);

        TotalStatistics total = new TotalStatistics();
        total.setSites(configSites.size());

        int totalPages = 0;
        int totalLemmas = 0;  // Если есть леммы, можно считать
        boolean indexing = false;

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for (Site site : configSites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());

            SiteEntity siteEntity = siteEntities.stream()
                    .filter(se -> se.getUrl().equals(site.getUrl()))
                    .findFirst().orElse(null);

            if (siteEntity != null) {
                int pagesCount = pageRepository.countBySite(siteEntity);
                item.setPages(pagesCount);
                // TODO: заменить на реальный подсчёт лемм, если есть
                int lemmasCount = 0;
                item.setLemmas(lemmasCount);

                item.setStatus(siteEntity.getStatus().name());
                item.setError(siteEntity.getLastError() == null ? "" : siteEntity.getLastError());
                item.setStatusTime(siteEntity.getStatusTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

                totalPages += pagesCount;
                totalLemmas += lemmasCount;

                if (siteEntity.getStatus() == searchengine.model.StatusEntity.INDEXING) {
                    indexing = true;
                }
            } else {
                // Если сайт ещё не в базе — ставим 0 и статус UNKNOWN
                item.setPages(0);
                item.setLemmas(0);
                item.setStatus("UNKNOWN");
                item.setError("");
                item.setStatusTime(System.currentTimeMillis());
            }

            detailed.add(item);
        }

        total.setPages(totalPages);
        total.setLemmas(totalLemmas);
        total.setIndexing(indexing);

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(data);
        response.setResult(true);

        return response;
    }
}