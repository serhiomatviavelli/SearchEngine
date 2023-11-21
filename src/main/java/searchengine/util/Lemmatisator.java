package searchengine.util;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
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

    private final LuceneMorphology luceneMorphRus = new RussianLuceneMorphology();
    private final LuceneMorphology luceneMorphEng = new EnglishLuceneMorphology();

    public Lemmatisator() throws IOException {
    }

    /**
     * Метод, разделяющий текст на леммы.
     * @param text - текст.
     * @return - список лемм.
     */
    public HashMap<String, Integer> splitTextInToLemmas(String text) {
        HashMap<String, Integer> result = new HashMap<>();
        List<String> words = getWordsFromString(text);
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
            if (element.is("meta,script,img,style,form,hidden,title")) {
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
                !getWordInfo(str).equals(str.trim() + "|l ПРЕДЛ") &&
                !getWordInfo(str).equals(str.trim() + "|1 CONJ");
    }

    /**
     * Метод, возвращающий лемму слова.
     * @param word - слово.
     * @return - лемма.
     */
    public String getLemma(String word) {
        if (isRussianWord(word)) {
            return luceneMorphRus.getNormalForms(word).get(0).trim();
        } else {
            return luceneMorphEng.getNormalForms(word).get(0).trim();
        }
    }

    /**
     * Метод, возвращающий информацию о слове.
     * @param word - слово.
     * @return - информация.
     */
    public String getWordInfo(String word) {
        if (isRussianWord(word)) {
            return luceneMorphRus.getMorphInfo(word.trim()).get(0);
        } else {
            return luceneMorphEng.getMorphInfo(word.trim()).get(0);
        }
    }

    /**
     * Возвращает слова, написанные кириллицей.
     * @param str - строка.
     * @return - список слов на кириллице.
     */
    public List<String> getWordsFromString(String str) {
        List<String> words = new ArrayList<>();
        String text = clearFromTags(str).replaceAll("[^a-zA-Zа-яА-ЯЁё\\s]", "").toLowerCase().trim();
        String[] array = text.split("\\s+");
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
        List<String> forms;
        if (isRussianWord(word)) {
            forms = luceneMorphRus.getNormalForms(word);
        } else {
            forms = luceneMorphEng.getNormalForms(word);
        }
        List<String> words = getWordsFromString(text);
        for (String form : forms) {
            for (String wordFromText : words) {
                if (isRussianWord(wordFromText) && luceneMorphRus.getNormalForms(wordFromText).get(0).equals(form)) {
                    return wordFromText;
                } else if (!isRussianWord(wordFromText) && luceneMorphEng.getNormalForms(wordFromText).get(0).equals(form)) {
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

    /**
     * Метод, проверяющий русское ли слово задано.
     * @param word - слово, которое необходимо проверить.
     * @return - true, если слово русское.
     */
    public boolean isRussianWord(String word) {
        return word.matches("[а-яА-ЯёЁ]{" + word.length() + "}");
    }
}
