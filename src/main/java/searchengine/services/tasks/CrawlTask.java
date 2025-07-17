package searchengine.services.tasks;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CrawlTask extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final AtomicBoolean stopRequested;
    private final SitesList sitesList;
    private final int depth;
    private static final AtomicInteger skippedContentCount = new AtomicInteger(0);
    private static final int MAX_LOGGED_SKIPPED = 10;
    private final UrlFilter urlFilter;

    private static final int MAX_DEPTH = 100;
    private static final Set<String> visited = ConcurrentHashMap.newKeySet();

    public CrawlTask(String url,
                     SiteEntity siteEntity,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     AtomicBoolean stopRequested,
                     SitesList sitesList, UrlFilter urlFilter) {
        this(url, siteEntity, pageRepository, siteRepository, stopRequested, sitesList, 0, urlFilter);
    }

    public CrawlTask(String url,
                     SiteEntity siteEntity,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     AtomicBoolean stopRequested,
                     SitesList sitesList,
                     int depth, UrlFilter urlFilter) {
        this.url = url;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.stopRequested = stopRequested;
        this.sitesList = sitesList;
        this.depth = depth;
        this.urlFilter = urlFilter;
    }

    @Override
    protected void compute() {
        if (shouldSkipTask()) return;

        long start = System.currentTimeMillis();

        try {
            Connection.Response response;
            try {
                response = connectToPage();
            } catch (UnsupportedMimeTypeException e) {
                log.debug("Пропущен URL с неподдерживаемым типом контента: {} [{}]", url, e.getMimeType());
                return;
            }
            if (response == null) return;

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 400) {
                processPage(response);
            } else {
                log.warn("Получен код {}, страница {} сохраняется с пустым контентом", statusCode, url);
                savePage(url, "", statusCode);
            }

        } catch (Exception e) {
            String message = "Ошибка при обходе " + url + ": " + e.getMessage();
            log.error(message, e);
            siteEntity.setLastError(message);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("⏱️ Обработка URL завершена: {} за {} мс, глубина {}, посещено {} страниц",
                    url, duration, depth, visited.size());
        }
    }


    private boolean shouldSkipTask() {
        return stopRequested.get() || depth > MAX_DEPTH || !visited.add(url);
    }

    private Connection.Response connectToPage() throws Exception {
        String userAgent = sitesList.getUserAgent() != null
                ? sitesList.getUserAgent()
                : "HeliontSearchBot/1.0 (+http://yourdomain.com/bot)";

        String referrer = sitesList.getReferrer() != null
                ? sitesList.getReferrer()
                : "http://www.google.com";

        Connection.Response response = Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(10_000)
                .ignoreHttpErrors(true)
                .execute();

        String contentType = response.contentType();
        if (contentType == null ||
                (!contentType.startsWith("text/") &&
                        !contentType.startsWith("application/xml") &&
                        !contentType.contains("+xml"))) {

            if (skippedContentCount.incrementAndGet() <= MAX_LOGGED_SKIPPED) {
                log.debug("⛔ Пропущен неподдерживаемый тип контента '{}' для URL: {}", contentType, url);
            } else if (skippedContentCount.get() == MAX_LOGGED_SKIPPED + 1) {
                log.debug("⚠️ Достигнут лимит логирования для неподдерживаемых типов контента.");
            }

            throw new UnsupportedMimeTypeException("Неподдерживаемый тип контента: " + contentType, contentType, url);
        }

        return response;
    }
    private void processPage(Connection.Response response) throws Exception {
        Document doc = response.parse();
        savePage(url, doc.html(), response.statusCode());

        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 501)); // задержка для имитации человека

        Elements links = doc.select("a[href]");
        Set<CrawlTask> subtasks = ConcurrentHashMap.newKeySet();

        for (var link : links) {
            String absHref = link.attr("abs:href");

            if (!absHref.startsWith(siteEntity.getUrl())) {
                continue; // внешний домен
            }

            if (visited.contains(absHref)) {
                continue; // уже посещено
            }

            if (urlFilter.isSkippable(absHref)) {
                log.debug("⛔ Пропущен URL по фильтру: {}", absHref);
                continue;
            }

            String path = getRelativePath(absHref);
            if (pageRepository.existsByPathAndSite(path, siteEntity)) {
                continue; // уже есть в БД
            }

            CrawlTask task = new CrawlTask(
                    absHref,
                    siteEntity,
                    pageRepository,
                    siteRepository,
                    stopRequested,
                    sitesList,
                    depth + 1,
                    urlFilter
            );
            subtasks.add(task);
        }

        log.debug("🔗 Найдено ссылок на странице {}: {}, запланировано подзадач: {}", url, links.size(), subtasks.size());
        invokeAll(subtasks);
    }


    private void savePage(String url, String html, int statusCode) {
        try {
            URI base = new URI(siteEntity.getUrl());
            URI pageUri = new URI(url);
            String path = base.relativize(pageUri).toString();
            if (!path.startsWith("/")) path = "/" + path;

            if (pageRepository.existsByPathAndSite(path, siteEntity)) {
                log.debug("Страница уже существует: {}", path);
                return;
            }

            PageEntity page = new PageEntity();
            page.setSite(siteEntity);
            page.setPath(path);
            page.setContent(html);
            page.setCode(statusCode);

            pageRepository.save(page);
            log.info("Сохранена страница: {} (код {})", path, statusCode);

        } catch (DataIntegrityViolationException e) {
            log.warn("⚠️ Уникальность нарушена при сохранении страницы {}: {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("⚠️ Не удалось сохранить страницу {}: {}", url, e.getMessage(), e);
        }
    }


    private String getRelativePath(String absUrl) {
        try {
            URI base = new URI(siteEntity.getUrl());
            URI pageUri = new URI(absUrl);
            String path = base.relativize(pageUri).toString();
            return path.startsWith("/") ? path : "/" + path;
        } catch (Exception e) {
            return "/";
        }
    }
}
