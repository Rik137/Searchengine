package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.LocalDateTime;
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
    private Thread animationThread;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ConcurrentMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    @Override
    public boolean startIndexing() {
        String[] dots = {"   ", ".  ", ".. ", "..."};
        for (int i = 0; i < 8; i++) {
            System.out.print("\r🔄 НАЧАЛАСЬ ИНДЕКСАЦИЯ" + dots[i % dots.length]);
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("\r▶️ Запуск индексации...     ");
        log.info(">>> Запущен метод startIndexing()");
        if (!isIndexing.compareAndSet(false, true)) {
            log.warn("Попытка запустить индексацию, но она уже выполняется");
            return false;
        }

        log.info("▶️ Запуск полной индексации всех сайтов");
        stopRequested.set(false);
        List<Site> sites = sitesList.getSites();

        for (Site site : sites) {
            siteDataService.deleteOldSiteData(site.getUrl());

            Future<?> future = executor.submit(() -> indexSite(site));
            runningTasks.put(site.getUrl(), future);
        }

        return true;
    }

    @Transactional
    protected void indexSite(Site site) {
        String url = site.getUrl();
        log.info("🟡 Начало индексации сайта: {}", url);

        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(url);
        siteEntity.setName(site.getName());
        siteEntity.setStatus(StatusEntity.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        try {
            UrlFilter urlFilter = new UrlFilter();
            CrawlTask rootTask = new CrawlTask(
                    url,
                    siteEntity,
                    pageRepository,
                    siteRepository,
                    stopRequested,
                    sitesList,
                    urlFilter
            );

            ForkJoinPool pool = new ForkJoinPool();
            pool.invoke(rootTask);

            if (stopRequested.get()) {
                siteEntity.setStatus(StatusEntity.FAILED);
                siteEntity.setLastError("Индексация остановлена пользователем");
                log.info("⛔ Индексация остановлена пользователем для сайта {}", url);
            } else {
                siteEntity.setStatus(StatusEntity.INDEXED);
                siteEntity.setLastError(null);
                log.info("✅ Индексация завершена успешно: {}", url);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка индексации сайта {}: {}", url, e.getMessage(), e);
            siteEntity.setStatus(StatusEntity.FAILED);
            siteEntity.setLastError("Ошибка: " + e.getMessage());
        } finally {
            runningTasks.remove(url);
            if (runningTasks.isEmpty()) {
                isIndexing.set(false);
                log.info("⏹️ Индексация завершена для всех сайтов");
            }

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
}



