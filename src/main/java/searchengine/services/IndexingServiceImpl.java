package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.StatusEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.serviceinterfaces.IndexingService;
import searchengine.services.tasks.CrawlTask;
import searchengine.services.tasks.UrlFilter;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataService siteDataService;
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PageService pageService;

    private final ForkJoinPool pool = new ForkJoinPool();
    private final ConcurrentMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);


    @Override
    public boolean startIndexing() {
        log.info(">>> Запущен метод startIndexing()");

        if (!isIndexing.compareAndSet(false, true)) {
            log.warn("Индексация уже запущена");
            return false;
        }

        stopRequested.set(false);

        List<Site> sites = sitesList.getSites();

        clearOldSiteData(sites);
        List<CrawlTask> tasks = createCrawlTasks(sites);

        pool.invoke(new RecursiveAction() {
            @Override
            protected void compute() {
                invokeAll(tasks);
            }
        });

        updateSiteStatusesAfterIndexing();

        isIndexing.set(false);
        log.info("⏹️ Индексация завершена для всех сайтов");

        return true;
    }

    private void clearOldSiteData(List<Site> sites) {
        for (Site site : sites) {
            siteDataService.deleteOldSiteData(site.getUrl());
        }
    }

    private SiteEntity findOrCreateSiteEntity(Site site) {
        String normalizedUrl = normalizeUrl(site.getUrl());
        SiteEntity siteEntity = siteRepository.findByUrl(normalizedUrl);
        if (siteEntity == null) {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(normalizedUrl);
            siteEntity.setName(site.getName());
        }
        siteEntity.setStatus(StatusEntity.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity = siteRepository.save(siteEntity);
        return siteEntity;
    }


    public String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.length() > 1 && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private void updateSiteStatusesAfterIndexing() {
        if (stopRequested.get()) {
            updateSitesStatus(StatusEntity.FAILED, "Индексация остановлена пользователем");
        } else {
            updateSitesStatus(StatusEntity.INDEXED, null);
        }
    }

    private void updateSitesStatus(StatusEntity status, String errorMessage) {
        List<SiteEntity> indexingSites = siteRepository.findAllByStatus(StatusEntity.INDEXING);
        LocalDateTime now = LocalDateTime.now();
        for (SiteEntity siteEntity : indexingSites) {
            siteEntity.setStatus(status);
            siteEntity.setLastError(errorMessage);
            siteEntity.setStatusTime(now);
            siteRepository.save(siteEntity);
            log.info("Статус сайта {} обновлён на {}", siteEntity.getUrl(), status);
        }
    }


    @Override
    public boolean isIndexing() {
        return isIndexing.get();
    }

    @Override
    public boolean stopIndexing() {
        if (!isIndexing.get()) {
            log.warn("Попытка остановить индексацию, но она не запущена");
            return false;
        }
        stopRequested.set(true);
        log.info("🛑 Остановка всех задач индексации...");

        for (Future<?> future : runningTasks.values()) {
            future.cancel(true);
        }
        runningTasks.clear();

        List<SiteEntity> indexingSites = siteRepository.findAllByStatus(StatusEntity.INDEXING);
        LocalDateTime now = LocalDateTime.now();
        for (SiteEntity siteEntity : indexingSites) {
            siteEntity.setStatus(StatusEntity.FAILED);
            siteEntity.setLastError("Индексация остановлена пользователем");
            siteEntity.setStatusTime(now);
            siteRepository.save(siteEntity);
            log.info("⛔ Статус сайта {} обновлён на FAILED", siteEntity.getUrl());
        }

        isIndexing.set(false);
        log.info("Индексация успешно остановлена");
        return true;
    }

    @Override
    public boolean isPageWithinConfiguredSites(String url) {
        try {
            URI inputUri = new URI(url);
            String inputHost = normalizeHost(inputUri.getHost());

            if (inputHost == null) {
                log.warn("URL '{}' не содержит допустимого host", url);
                return false;
            }

            boolean matchFound = sitesList.getSites().stream().anyMatch(site -> {
                try {
                    URI siteUri = new URI(site.getUrl());
                    String siteHost = normalizeHost(siteUri.getHost());

                    return inputHost.equals(siteHost);
                } catch (URISyntaxException e) {
                    log.error("Ошибка парсинга site.url='{}': {}", site.getUrl(), e.getMessage());
                    return false;
                }
            });

            if (!matchFound) {
                log.info("URL '{}' (host: '{}') не соответствует ни одному сайту из конфигурации", url, inputHost);
            }

            return matchFound;

        } catch (URISyntaxException e) {
            log.error("Некорректный URL '{}': {}", url, e.getMessage());
            return false;
        }
    }
    private String normalizeHost(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    private List<CrawlTask> createCrawlTasks(List<Site> sites) {
        List<CrawlTask> tasks = new ArrayList<>();
        for (Site site : sites) {
            SiteEntity siteEntity = findOrCreateSiteEntity(site);
            CrawlTask task = new CrawlTask(
                    site.getUrl(),
                    siteEntity,
                    pageRepository,
                    siteRepository,
                    stopRequested,
                    sitesList,
                    0,
                    new UrlFilter(),
                    pageService
            );
            tasks.add(task);
        }
        return tasks;
    }
    @Override
    @Transactional
    public boolean indexPage(String url) {
        log.info("🌐 Индексация отдельной страницы: {}", url);

        if (!isPageWithinConfiguredSites(url)) {
            log.warn("⛔ Страница {} вне списка разрешённых", url);
            return false;
        }

        try {

            String html = Jsoup.connect(url).get().html();


            String text = Jsoup.parse(html).text();


            Site siteConfig = sitesList.getSites().stream()
                    .filter(s -> url.startsWith(s.getUrl()))
                    .findFirst()
                    .orElseThrow();

            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl());
            if (siteEntity == null) {
                siteEntity = new SiteEntity();
                siteEntity.setUrl(siteConfig.getUrl());
                siteEntity.setName(siteConfig.getName());
                siteEntity.setStatus(StatusEntity.INDEXED);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }


            log.info("✅ Страница успешно проиндексирована: {}", url);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }
}



