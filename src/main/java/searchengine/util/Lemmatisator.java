package searchengine.util;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Сервис для работы с морфологическими формами.
 */
@Component
public class Lemmatisator {

    private final LuceneMorphology luceneMorph = new RussianLuceneMorphology();

    public Lemmatisator() throws IOException {
    }

    /**
     * Метод, разделяющий текст на леммы.
     * @param text - текст.
     * @return - список лемм.
     */
    public HashMap<String, Integer> splitTextInToLemmas(String text) {
        HashMap<String, Integer> result = new HashMap<>();
        List<String> words = getRussianWordsFromString(text);
        for (String word : words) {
            int count = 0;
            String normalForm = getLemma(word);

            for (String s : words) {
                if (getLemma(s).contains(normalForm)) {
                    count++;
                }
            }
            result.put(normalForm, count);
        }
        return result;
    }

    /**
     * Метод, очищающий html-код от тегов.
     * @param htmlCode - код.
     * @return - чистый текст.
     */
    public String clearFromTags(String htmlCode) {
        Document doc = Jsoup.parse(htmlCode);
        Elements elements = doc.select("*");
        for (Element element : elements) {
            if (element.is("meta,script,img,style,form,hidden")) {
                element.remove();
            }
        }
        return doc.text();
    }

    /**
     * Метод, определяющий является ли строка словом или частицей (предлогом, местоимением и т.д.).
     * @param str - входящая строка.
     * @return - true если строка является словом.
     */
    public boolean isWord(String str) {
        return !getWordInfo(str).equals(str.trim() + "|n СОЮЗ") &&
                !getWordInfo(str).equals(str.trim() + "|o МЕЖД") &&
                !getWordInfo(str).equals(str.trim() + "|l ПРЕДЛ");
    }

    /**
     * Метод, возвращающий лемму слова.
     * @param word - слово.
     * @return - лемма.
     */
    public String getLemma(String word) {
        return luceneMorph.getNormalForms(word).get(0).trim();
    }

    /**
     * Метод, возвращающий информацию о слове.
     * @param word - слово.
     * @return - информация.
     */
    public String getWordInfo(String word) {
        return luceneMorph.getMorphInfo(word.trim()).get(0);
    }

    /**
     * Возвращает слова, написанные кириллицей.
     * @param str - строка.
     * @return - список слов на кириллице.
     */
    public List<String> getRussianWordsFromString(String str) {
        List<String> words = new ArrayList<>();
        String onlyRussianWords = clearFromTags(str).replaceAll("[^а-яА-ЯЁё\\s]", "").toLowerCase().trim();
        String[] array = onlyRussianWords.split("\\s+");
        for (String word : array) {
            if(isWord(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Метод, совершающий поиск в тексте однокоренных слов.
     * @param text - текст.
     * @param word - слово.
     * @return - однокоренное слово из текста, либо слово из параметров.
     */
    public String getCognateWord(String text, String word) {
        List<String> forms = luceneMorph.getNormalForms(word);
        List<String> words = getRussianWordsFromString(text);
        for (String form : forms) {
            for (String wordFromText : words) {
                if (luceneMorph.getNormalForms(wordFromText).get(0).equals(form)) {
                    return wordFromText;
                }
            }
        }
        return word;
    }

    /**
     * Метод, вычисляющий леммы на одной странице.
     * @param code - код страницы.
     * @return - список лемм.
     */
    public HashMap<String, Integer> getLemmasList(String code) {
        return splitTextInToLemmas(code);
    }
}
