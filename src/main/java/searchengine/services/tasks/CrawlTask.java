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
import searchengine.services.PageService;

import java.io.IOException;
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
    private final UrlFilter urlFilter;
    private final PageService pageService; // добавлено

    private static final int MAX_DEPTH = 100;
    private static final Set<String> visited = ConcurrentHashMap.newKeySet();

    public CrawlTask(String url,
                     SiteEntity siteEntity,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     AtomicBoolean stopRequested,
                     SitesList sitesList,
                     int depth,
                     UrlFilter urlFilter,
                     PageService pageService) {
        this.url = url;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.stopRequested = stopRequested;
        this.sitesList = sitesList;
        this.depth = depth;
        this.urlFilter = urlFilter;
        this.pageService = pageService;
    }



    @Override
    protected void compute() {
        if (shouldSkipTask()) return;

        long start = System.currentTimeMillis();

        try {
            Thread.sleep(randomDelay());

            Connection.Response response = fetchPage();
            if (response == null) return;

            handleResponse(response);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Обход прерван на задержке для URL: {}", url);
        } catch (Exception e) {
            handleError(e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("⏱️ Обработка URL завершена: {} за {} мс, глубина {}, посещено {} страниц",
                    url, duration, depth, visited.size());
        }
    }

    private long randomDelay() {
        return 500 + (long) (Math.random() * 4500);
    }

    private Connection.Response fetchPage() throws IOException {
        try {
            String userAgent = sitesList.getUserAgent() != null ? sitesList.getUserAgent() : "HeliontSearchBot/1.0 (+http://yourdomain.com/bot)";
            String referrer = sitesList.getReferrer() != null ? sitesList.getReferrer() : "http://www.google.com";

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() >= 400) {
                log.warn("HTTP ошибка {} при доступе к {}", response.statusCode(), url);
            }

            String contentType = response.contentType();
            if (contentType == null || (!contentType.startsWith("text/") &&
                    !contentType.startsWith("application/xml") &&
                    !contentType.contains("+xml"))) {
                log.warn("Неподдерживаемый тип контента '{}' для URL: {}", contentType, url);
                throw new UnsupportedMimeTypeException("Неподдерживаемый тип контента: " + contentType, contentType, url);
            }

            return response;
        } catch (UnsupportedMimeTypeException e) {
            log.debug("Пропущен URL с неподдерживаемым типом контента: {} [{}]", url, e.getMimeType());
            return null;
        }
    }

    private void handleResponse(Connection.Response response) throws Exception {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 400) {
            Document doc = response.parse();
            savePage(url, doc.html(), statusCode);

            if (!pageService.indexSinglePage(url)) {
                log.warn("Индексация страницы не удалась: {}", url);
            }

            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 501));

            createSubTasks(doc);
        } else {
            log.warn("Получен код {}, страница {} сохраняется с пустым контентом", statusCode, url);
            savePage(url, "", statusCode);
        }
    }

    private void createSubTasks(Document doc) {
        Elements links = doc.select("a[href]");
        Set<CrawlTask> subtasks = ConcurrentHashMap.newKeySet();

        for (var link : links) {
            String absHref = link.attr("abs:href");

            if (!absHref.startsWith(siteEntity.getUrl())) continue;
            if (visited.contains(absHref)) continue;
            if (urlFilter.isSkippable(absHref)) continue;

            String path = getRelativePath(absHref);
            if (pageRepository.existsByPathAndSiteId(path, siteEntity.getId())){
                log.debug("Страница уже существует: {}", path);
                return;
            }

            subtasks.add(new CrawlTask(
                    absHref,
                    siteEntity,
                    pageRepository,
                    siteRepository,
                    stopRequested,
                    sitesList,
                    depth + 1,
                    urlFilter,
                    pageService
            ));
        }

        invokeAll(subtasks);
    }

    private void handleError(Exception e) {
        String message = "Ошибка при обходе " + url + ": " + e.getMessage();
        log.error(message, e);
        siteEntity.setLastError(message);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    private boolean shouldSkipTask() {
        return stopRequested.get() || depth > MAX_DEPTH || !visited.add(url);
    }

    private Connection.Response connectToPage() throws IOException {
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
        if (response.statusCode() >= 400) {
            log.warn("Got HTTP error {} when accessing {}", response.statusCode(), url);
        }
        String contentType = response.contentType();
        if (contentType == null ||
                (!contentType.startsWith("text/") &&
                        !contentType.startsWith("application/xml") &&
                        !contentType.contains("+xml"))) {
            log.warn("Unsupported content type '{}' at URL: {}", contentType, url);
            throw new UnsupportedMimeTypeException("Неподдерживаемый тип контента: " + contentType, contentType, url);
        }

        return response;
    }

    private void processPage(Connection.Response response) throws Exception {
        Document doc = response.parse();
        savePage(url, doc.html(), response.statusCode());

        // Вызов индексации страницы
        boolean indexed = pageService.indexSinglePage(url);
        if (!indexed) {
            log.warn("Индексация страницы не удалась: {}", url);
        }

        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 501)); // задержка

        Elements links = doc.select("a[href]");
        Set<CrawlTask> subtasks = ConcurrentHashMap.newKeySet();

        for (var link : links) {
            String absHref = link.attr("abs:href");

            if (!absHref.startsWith(siteEntity.getUrl())) continue;
            if (visited.contains(absHref)) continue;
            if (urlFilter.isSkippable(absHref)) continue;

            String path = getRelativePath(absHref);
            if (pageRepository.existsByPathAndSiteId(path, siteEntity.getId())) continue;

            CrawlTask task = new CrawlTask(
                    absHref,
                    siteEntity,
                    pageRepository,
                    siteRepository,
                    stopRequested,
                    sitesList,
                    depth + 1,
                    urlFilter,
                    pageService
            );
            subtasks.add(task);
        }

        invokeAll(subtasks);
    }

    private void savePage(String url, String html, int statusCode) {
        try {
            URI base = new URI(siteEntity.getUrl());
            URI pageUri = new URI(url);
            String path = base.relativize(pageUri).toString();
            if (!path.startsWith("/")) path = "/" + path;

            if (pageRepository.existsByPathAndSiteId(path, siteEntity.getId())) {
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
            log.warn("Уникальность нарушена при сохранении страницы {}: {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Не удалось сохранить страницу {}: {}", url, e.getMessage(), e);
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
