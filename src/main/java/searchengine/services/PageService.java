package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.lemmatizer.LemmaExtractor;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SearchIndexEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository indexRepository;
    private final LemmaExtractor lemmaExtractor;
    private final SiteRepository siteRepository;

    private static final int CONNECTION_TIMEOUT = 10_000;
    private static final String USER_AGENT = "HeliontSearchBot/1.0 (+http://yourdomain.com/bot)";

    @Transactional
    public boolean indexSinglePage(String url) {
        log.info("🌐 Индексация страницы: {}", url);

        try {
            String html = loadPageContent(url);
            if (html == null) {
                log.warn("Страница не загружена или пустая: {}", url);
                return false;
            }

            String text = lemmaExtractor.extractPlainTextFromHtml(html);

            SiteEntity site = getSiteEntity(url);
            if (site == null) {
                log.warn("❌ Сайт не найден в базе: {}", url);
                return false;
            }

            String path = getPathFromUrl(url);

            deleteExistingPageData(site, path);

            if (text.isBlank()) {
                log.warn("⚠️ Текст страницы пуст после очистки: {}", url);
                return true;
            }

            PageEntity page = saveOrUpdatePageEntity(site, path, html);

            processLemmas(site, page, text);

            log.info("✅ Индексация завершена для страницы: {}", url);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }


    private String loadPageContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECTION_TIMEOUT)
                    .get();
            return doc.html();
        } catch (IOException e) {
            log.error("Ошибка загрузки страницы {}: {}", url, e.getMessage());
            return null;
        }
    }

    private SiteEntity getSiteEntity(String pageUrl) {
        try {
            URI uri = new URI(pageUrl);
            String baseUrl = uri.getScheme() + "://" + uri.getHost();
            baseUrl = normalizeUrl(baseUrl);
            log.info("Ищем сайт в БД по URL: '{}'", baseUrl);
            return siteRepository.findByUrl(baseUrl);
        } catch (URISyntaxException e) {
            log.error("Некорректный URL: {}", pageUrl, e);
            return null;
        }
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
    private String getPathFromUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return path;
    }

    @Transactional
    protected void deleteExistingPageData(SiteEntity site, String path) {
        pageRepository.findBySiteIdAndPath(site.getId(), path)
                .ifPresent(page -> pageRepository.delete(page));
    }

    @Transactional
    protected PageEntity saveOrUpdatePageEntity(SiteEntity site, String path, String html) {
        return pageRepository.findBySiteIdAndPath(site.getId(), path)
                .map(existing -> {
                    existing.setCode(200);
                    existing.setContent(html);
                    return pageRepository.save(existing);
                })
                .orElseGet(() -> {
                    PageEntity newPage = new PageEntity();
                    newPage.setSite(site);
                    newPage.setPath(path);
                    newPage.setCode(200);
                    newPage.setContent(html);
                    return pageRepository.save(newPage);
                });
    }

    @Transactional
    protected void processLemmas(SiteEntity site, PageEntity page, String text) {
        log.info("processLemmas: Site class = {}, hash = {}", site.getClass(), System.identityHashCode(site));

        // Принудительно обновим entity из базы, чтобы гарантировать, что он привязан к текущему контексту
        SiteEntity managedSite = siteRepository.findById(site.getId())
                .orElseThrow(() -> new IllegalStateException("Site not found by id: " + site.getId()));

        Map<String, Integer> lemmaFreqs = lemmaExtractor.getLemmaFrequencies(text);
        if (lemmaFreqs.isEmpty()) {
            log.warn("⚠️ Леммы не найдены, возможно, страница пуста или содержит только служебные слова.");
            return;
        }

        Map<String, LemmaEntity> existing = lemmaRepository.findAllBySite(managedSite).stream()
                .collect(Collectors.toMap(LemmaEntity::getLemma, Function.identity()));

        List<LemmaEntity> lemmasToSave = new ArrayList<>();
        List<SearchIndexEntity> indexesToSave = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmaFreqs.entrySet()) {
            String lemmaText = entry.getKey();
            int freq = entry.getValue();

            LemmaEntity lemma = existing.get(lemmaText);
            if (lemma == null) {
                lemma = new LemmaEntity();
                lemma.setLemma(lemmaText);
                lemma.setSite(managedSite);
                lemma.setFrequency(freq);
                lemmasToSave.add(lemma);
                existing.put(lemmaText, lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() + freq);
                lemmasToSave.add(lemma);
            }

            indexesToSave.add(new SearchIndexEntity(page, freq, lemma));
        }

        lemmaRepository.saveAll(lemmasToSave);
        indexRepository.saveAll(indexesToSave);
    }


    @Transactional
    protected void deletePageData(PageEntity page) {
        log.info("Удаляем индексы для страницы с id = {}", page.getId());
        log.info("Удаляем страницу с id = {}", page.getId());
        indexRepository.deleteByPageId(page.getId());
        pageRepository.delete(page);
    }
}

