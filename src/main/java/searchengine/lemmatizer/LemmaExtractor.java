package searchengine.lemmatizer;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Класс для извлечения лемм из текста с подсчётом их частоты.
 * Исключает служебные части речи: союзы, предлоги, частицы и т.п.
 */
@Slf4j
@Component
public class LemmaExtractor {

    private static final int MIN_WORD_LENGTH = 2;
    private static final Pattern NON_RUSSIAN_CHARS = Pattern.compile("[^а-яё\\s]+");

    private final LuceneMorphology morphology;

    private static final Set<String> EXCLUDED_PARTS = Set.of(
            "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МЕЖД", "МС"
    );

    public LemmaExtractor() {
        try {
            this.morphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            log.error("Ошибка инициализации LuceneMorphology", e);
            throw new IllegalStateException("Не удалось инициализировать морфологию", e);
        }
    }

    public Map<String, Integer> getLemmaFrequencies(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            String cleanedText = extractPlainTextFromHtml(text);
            String[] words = preprocessText(cleanedText);
            List<String> lemmas = generateLemmas(words);
            return calculateLemmaFrequencies(lemmas);
        } catch (Exception e) {
            log.error("Ошибка при извлечении лемм", e);
            return Collections.emptyMap();
        }
    }

    private String[] preprocessText(String text) {
        return NON_RUSSIAN_CHARS.matcher(text.toLowerCase(Locale.ROOT).trim()).replaceAll("").split("\\s+");
    }

    private List<String> generateLemmas(String[] words) {
        List<String> lemmas = new ArrayList<>();
        for (String word : words) {
            if (word.length() < MIN_WORD_LENGTH) continue;

            List<String> morphInfo = morphology.getMorphInfo(word);
            if (containsExcludedPartOfSpeech(morphInfo)) continue;

            List<String> normalForms = morphology.getNormalForms(word);
            if (!normalForms.isEmpty()) {
                lemmas.add(normalForms.get(0));
            }
        }
        return lemmas;
    }

    private boolean containsExcludedPartOfSpeech(List<String> morphInfoList) {
        return morphInfoList.stream()
                .map(s -> s.toUpperCase(Locale.ROOT))
                .anyMatch(info -> EXCLUDED_PARTS.stream().anyMatch(info::contains));
    }

    private Map<String, Integer> calculateLemmaFrequencies(List<String> lemmas) {
        Map<String, Integer> freq = new HashMap<>();
        for (String lemma : lemmas) {
            freq.merge(lemma, 1, Integer::sum);
        }
        return freq;
    }

    public String extractPlainTextFromHtml(String htmlText) {
        if (htmlText == null) return "";
        return Jsoup.parse(htmlText).text().replaceAll("\\s+", " ").trim();
    }
}