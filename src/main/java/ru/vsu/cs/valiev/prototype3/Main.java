package ru.vsu.cs.valiev.prototype3;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

public class Main {

    // ---------- ЯЗЫК ИНТЕРФЕЙСА ----------
    private enum Lang { RU, EN, JP }
    private static Lang uiLang = Lang.RU;

    private static String tr(String ru, String en, String jp) {
        if (uiLang == Lang.RU) return ru;
        if (uiLang == Lang.EN) return en;
        return jp;
    }

    // ---------- ПРИВЕТСТВИЕ (случайный каомодзи) ----------
    private static final String[] KAOMOJI = {
            "(^_^)", "(＾▽＾)", "(・ω・)", "(￣▽￣)", "(＾ω＾)",
            "(≧▽≦)", "(・∀・)", "(＾ｖ＾)", "(￣ω￣)", "(＾Ｏ＾)", ":)"
    };
    private static String welcomeKaomoji = "(^_^)";

    // ---------- КОНТАКТЫ ----------
    private static final String TELEGRAM = "@val_vlad306";
    private static final String EMAIL = "ricejzxc@gmail.com";
    private static final String GITHUB = "RICEj306";

    // ---------- КОМАНДЫ И АЛИАСЫ ----------
    private static final String DEV_PREFIX = ".dev.config.";
    private static final String CONTACTS_COMMAND = ".contacts";
    private static final String CLEAR_COMMAND = ".clear";
    private static final String CLEAR_ALIAS = ".c";
    private static final String DEV_ALIAS_PREFIX = ".dc.";

    // ---------- КОЛЬЦА И АЛФАВИТЫ ----------
    private static final String RU_LETTERS = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
    private static final String EN_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String PUNCT = ".,!?;:-()\"'№#%&*@+/=<>_~^$";
    private static final String JP_PUNCT = "、。，．！？〈〉「」『』【】〜";
    private static final char MARKER = 'ヿ';
    private static final String HIRAGANA = buildRange(0x3041, 0x3096);
    private static final String KATAKANA = buildRange(0x30A1, 0x30FA);
    private static final String KANJI = buildRange(0x4E00, 0x9FFF);

    private static String buildRange(int from, int to) {
        StringBuilder sb = new StringBuilder(to - from + 1);
        for (int c = from; c <= to; c++) sb.append((char) c);
        return sb.toString();
    }

    private static String ringFor(char ch) {
        if (RU_LETTERS.indexOf(ch) >= 0) return RU_LETTERS;
        if (EN_LETTERS.indexOf(ch) >= 0) return EN_LETTERS;
        if (HIRAGANA.indexOf(ch) >= 0) return HIRAGANA;
        if (KATAKANA.indexOf(ch) >= 0) return KATAKANA;
        if (KANJI.indexOf(ch) >= 0) return KANJI;
        return null;
    }

    private static boolean isLetter(int ch) {
        return ringFor(Character.toUpperCase((char) ch)) != null;
    }

    private static int scriptOf(String word) {
        for (char ch : word.toCharArray()) {
            String ring = ringFor(Character.toUpperCase(ch));
            if (ring == RU_LETTERS) return 0;
            if (ring == EN_LETTERS) return 1;
            if (ring != null) return 2;
        }
        return -1;
    }

    // ---------- СЛОВАРИ ----------
    private static final String RU_DICT = "/russian-utf8.txt";
    private static final String EN_DICT = "/english-utf8.txt";
    private static final String JP_DICT = "/japanese-utf8.txt";

    private static final Map<Integer, List<String>> wordsByLen = new HashMap<>();
    private static final Map<Integer, Map<String, Integer>> indexByLen = new HashMap<>();

    private static int bucketKey(int script, int len) { return script * 10000 + len; }

    private static void addWordToDict(String line, int script) {
        String word = line.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) return;
        for (char ch : word.toCharArray()) if (!isLetter(ch)) return;
        if (scriptOf(word) != script) return;
        int key = bucketKey(script, word.length());
        Map<String, Integer> idx = indexByLen.computeIfAbsent(key, k -> new HashMap<>());
        if (idx.containsKey(word)) return;
        List<String> list = wordsByLen.computeIfAbsent(key, k -> new ArrayList<>());
        idx.put(word, list.size());
        list.add(word);
    }

    private static int loadDict(String resource, int script) {
        int before = wordsByLen.values().stream().mapToInt(List::size).sum();
        try (InputStream in = Main.class.getResourceAsStream(resource)) {
            if (in == null) return 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) addWordToDict(line, script);
            }
        } catch (IOException e) {
            System.out.println(tr("Ошибка чтения словаря: ", "Dictionary read error: ", "辞書読み込みエラー: ") + e.getMessage());
        }
        return wordsByLen.values().stream().mapToInt(List::size).sum() - before;
    }

    private static int countWords(int script) {
        return wordsByLen.entrySet().stream()
                .filter(e -> e.getKey() / 10000 == script)
                .mapToInt(e -> e.getValue().size()).sum();
    }

    // ---------- КЛЮЧ ----------
    private enum KeyType { DATE, PASSWORD }
    private static KeyType keyType = KeyType.DATE;
    private static String keyPassword = "";
    private static LocalDate manualDate = null;
    private static String devPassword = "CHANGE_ME";

    private static int hashToInt(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            int v = ((h[0] & 0xFF) << 24) | ((h[1] & 0xFF) << 16)
                    | ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
            return v & 0x7FFFFFFF;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static LocalDate currentDate() {
        return manualDate != null ? manualDate : LocalDate.now();
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
    }

    private static int currentBase() {
        if (keyType == KeyType.PASSWORD && !keyPassword.isBlank()) return hashToInt(keyPassword);
        return hashToInt(currentDate().toString());
    }

    // ---------- ШИФРОВАНИЕ ----------
    public static String encrypt(String text) {
        int base = currentBase();
        StringBuilder out = new StringBuilder();
        int counter = 0;
        int i = 0, n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < n && Character.isDigit(text.charAt(j))) j++;
                for (int k = i; k < j; k++) {
                    int shift = Math.floorMod(base + counter, DIGITS.length());
                    int pos = DIGITS.indexOf(text.charAt(k));
                    out.append(DIGITS.charAt(Math.floorMod(pos + shift, DIGITS.length())));
                    counter++;
                }
                i = j;
            } else if (isLetter(ch)) {
                int j = i;
                while (j < n && isLetter(text.charAt(j))) j++;
                counter = encryptWord(text.substring(i, j), base, counter, out);
                i = j;
            } else if (PUNCT.indexOf(ch) >= 0 || JP_PUNCT.indexOf(ch) >= 0) {
                String ring = (PUNCT.indexOf(ch) >= 0) ? PUNCT : JP_PUNCT;
                int shift = Math.floorMod(base + counter, ring.length());
                int pos = ring.indexOf(ch);
                out.append(ring.charAt(Math.floorMod(pos + shift, ring.length())));
                counter++;
                i++;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static int encryptWord(String token, int base, int counter, StringBuilder out) {
        String lower = token.toLowerCase(Locale.ROOT);
        int script = scriptOf(lower);
        Integer idx = (script < 0) ? null
                : indexByLen.getOrDefault(bucketKey(script, lower.length()), Map.of()).get(lower);
        if (idx != null) {
            List<String> bucket = wordsByLen.get(bucketKey(script, lower.length()));
            int size = bucket.size();
            int shift = Math.floorMod(base + counter, size);
            out.append(bucket.get(Math.floorMod(idx + shift, size)));
            return counter + 1;
        }
        if (script == 2) out.append(MARKER);
        for (char c : token.toCharArray()) {
            char up = Character.toUpperCase(c);
            String ring = ringFor(up);
            if (ring == null) { out.append(c); counter++; continue; }
            int shift = Math.floorMod(base + counter, ring.length());
            int pos = ring.indexOf(up);
            out.append(ring.charAt(Math.floorMod(pos + shift, ring.length())));
            counter++;
        }
        return counter;
    }

    // ---------- РАСШИФРОВАНИЕ ----------
    public static String decrypt(String text) {
        int base = currentBase();
        StringBuilder out = new StringBuilder();
        int counter = 0;
        int i = 0, n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < n && Character.isDigit(text.charAt(j))) j++;
                for (int k = i; k < j; k++) {
                    int shift = Math.floorMod(base + counter, DIGITS.length());
                    int pos = DIGITS.indexOf(text.charAt(k));
                    out.append(DIGITS.charAt(Math.floorMod(pos - shift, DIGITS.length())));
                    counter++;
                }
                i = j;
            } else if (isLetter(ch) || ch == MARKER) {
                int j = i;
                while (j < n && (isLetter(text.charAt(j)) || text.charAt(j) == MARKER)) j++;
                counter = decryptWord(text.substring(i, j), base, counter, out);
                i = j;
            } else if (PUNCT.indexOf(ch) >= 0 || JP_PUNCT.indexOf(ch) >= 0) {
                String ring = (PUNCT.indexOf(ch) >= 0) ? PUNCT : JP_PUNCT;
                int shift = Math.floorMod(base + counter, ring.length());
                int pos = ring.indexOf(ch);
                out.append(ring.charAt(Math.floorMod(pos - shift, ring.length())));
                counter++;
                i++;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static int decryptWord(String token, int base, int counter, StringBuilder out) {
        boolean marked = token.charAt(0) == MARKER;
        String body = marked ? token.substring(1) : token;
        if (body.isEmpty()) { out.append(token); return counter; }
        int script = scriptOf(body);
        if (!marked && script >= 0) {
            String lower = body.toLowerCase(Locale.ROOT);
            boolean allUpper = body.equals(body.toUpperCase(Locale.ROOT)) && !body.equals(lower);
            if (!allUpper) {
                Integer idx = indexByLen.getOrDefault(bucketKey(script, body.length()), Map.of()).get(lower);
                if (idx != null) {
                    List<String> bucket = wordsByLen.get(bucketKey(script, body.length()));
                    int size = bucket.size();
                    int shift = Math.floorMod(base + counter, size);
                    out.append(bucket.get(Math.floorMod(idx - shift, size)));
                    return counter + 1;
                }
                if (script != 2) { out.append(token); return counter; }
            }
        }
        for (char c : body.toCharArray()) {
            char up = Character.toUpperCase(c);
            String ring = ringFor(up);
            if (ring == null) { out.append(c); counter++; continue; }
            int shift = Math.floorMod(base + counter, ring.length());
            int pos = ring.indexOf(up);
            out.append(ring.charAt(Math.floorMod(pos - shift, ring.length())));
            counter++;
        }
        return counter;
    }

    // ---------- ЭКРАН ----------
    private static void clearScreen() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                return;
            }
            System.out.print("\033[H\033[2J");
            System.out.flush();
        } catch (Exception e) {
            for (int i = 0; i < 40; i++) System.out.println();
        }
    }

    private static void printWelcome() {
        System.out.println(tr("Добро пожаловать в прототип 3 ",
                "Welcome to prototype 3 ", "プロトタイプ3へようこそ ") + welcomeKaomoji);
        System.out.println(tr("Поддерживаемые языки: RU, EN, JP",
                "Supported languages: RU, EN, JP", "対応言語: RU, EN, JP"));
    }

    // ---------- ПАМЯТЬ ----------
    private static Path configPath() {
        return Paths.get(System.getProperty("user.home"), ".shifrovka3", "config.properties");
    }

    private static void loadConfig() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(configPath())) {
            p.load(in);
            devPassword = p.getProperty("devPassword", devPassword);
            String lang = p.getProperty("uiLang", "RU");
            if (lang.equals("EN")) uiLang = Lang.EN;
            else if (lang.equals("JP")) uiLang = Lang.JP;
            else uiLang = Lang.RU;
            keyType = "PASSWORD".equals(p.getProperty("keyType")) ? KeyType.PASSWORD : KeyType.DATE;
            keyPassword = p.getProperty("keyPassword", "");
            String md = p.getProperty("manualDate", "");
            manualDate = md.isBlank() ? null : LocalDate.parse(md);
        } catch (IOException e) {
            // конфига ещё нет — первый запуск
        }
    }

    private static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("devPassword", devPassword);
        p.setProperty("uiLang", uiLang.name());
        p.setProperty("keyType", keyType.name());
        p.setProperty("keyPassword", keyPassword);
        p.setProperty("manualDate", manualDate == null ? "" : manualDate.toString());
        try {
            Files.createDirectories(configPath().getParent());
            try (OutputStream out = Files.newOutputStream(configPath())) {
                p.store(out, "Prototype 3 config");
            }
        } catch (IOException e) {
            System.out.println(tr("Не удалось сохранить конфиг: ", "Failed to save config: ", "設定を保存できません: ") + e.getMessage());
        }
    }

    // ---------- БУФЕР ОБМЕНА ----------
    private static void offerCopy(Scanner scanner, String text) {
        System.out.print(tr("[c] - скопировать в буфер обмена, [Enter] - дальше: ",
                "[c] - copy to clipboard, [Enter] - continue: ",
                "[c] クリップボードにコピー、[Enter] 続行: "));
        String a = scanner.nextLine().trim().toLowerCase(Locale.ROOT);
        if (a.equals("c") || a.equals("с")) {
            try {
                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                cb.setContents(new StringSelection(text), null);
                System.out.println(tr("Скопировано в буфер обмена.", "Copied to clipboard.", "クリップボードにコピーしました。"));
            } catch (Exception e) {
                System.out.println(tr("Буфер обмена недоступен: ", "Clipboard unavailable: ", "クリップボードを使用できません: ") + e.getMessage());
                System.out.println(tr("Скопируй вручную: ", "Copy manually: ", "手動でコピー: ") + text);
            }
        }
    }

    // ---------- АЛИАСЫ КОМАНД ----------
    private static String normalizeCommand(String line) {
        if (line.equals(CLEAR_ALIAS)) return CLEAR_COMMAND;
        if (line.startsWith(DEV_ALIAS_PREFIX)) {
            return DEV_PREFIX + line.substring(DEV_ALIAS_PREFIX.length());
        }
        return line;
    }

    // ---------- ПОТОКИ ----------
    private static void encryptFlow(Scanner scanner) {
        System.out.print(tr("Сообщение: ", "Message: ", "メッセージ: "));
        String message = scanner.nextLine();
        String encrypted = encrypt(message);
        System.out.println(tr("Шифр:       ", "Cipher:      ", "暗号文:    ") + encrypted);
        System.out.println(tr("Дата ключа: ", "Key date:   ", "鍵の日付:  ") + currentDate()
                + (manualDate != null
                ? tr(" (вручную)", " (manual)", " (手動)")
                : tr(" (автоматически)", " (automatic)", " (自動)")));
        offerCopy(scanner, encrypted);
    }

    private static void decryptFlow(Scanner scanner) {
        System.out.print(tr("Шифр: ", "Cipher: ", "暗号文: "));
        String encrypted = scanner.nextLine();
        System.out.print(tr("Дата шифрования (ГГГГ-ММ-ДД), Enter - текущая настройка: ",
                "Encryption date (YYYY-MM-DD), Enter for current setting: ",
                "暗号化日 (YYYY-MM-DD)、Enterで現在の設定: "));
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            try {
                manualDate = parseDate(input);
            } catch (Exception e) {
                System.out.println(tr("Не понял дату, использую текущую настройку.",
                        "Could not parse date, using current setting.",
                        "日付を解析できません。現在の設定を使用します。"));
            }
        }
        String decrypted = decrypt(encrypted);
        System.out.println(tr("Результат: ", "Result:   ", "結果:    ") + decrypted);
        offerCopy(scanner, decrypted);
    }

    // ---------- ПОДМЕНЮ ЯЗЫКА ----------
    private static void languageMenu(Scanner scanner) {
        clearScreen();
        while (true) {
            System.out.println(tr("=== ЯЗЫК ИНТЕРФЕЙСА ===", "=== INTERFACE LANGUAGE ===", "=== 界面言語 ==="));
            System.out.println("1 - Русский");
            System.out.println("2 - English");
            System.out.println("3 - 日本語");
            System.out.println(tr("0 - назад", "0 - back", "0 - 戻る"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String c = scanner.nextLine().trim();
            if (c.equals("1")) uiLang = Lang.RU;
            else if (c.equals("2")) uiLang = Lang.EN;
            else if (c.equals("3")) uiLang = Lang.JP;
            else if (c.equals("0")) {
                saveConfig();
                clearScreen();
                printWelcome();
                return;
            } else {
                System.out.println(tr("Неизвестный пункт", "Unknown item", "不明な項目"));
                continue;
            }
            saveConfig();
            clearScreen();
            printWelcome();
            return;
        }
    }

    // ---------- О ПРОГРАММЕ ----------
    private static void aboutScreen(Scanner scanner) {
        clearScreen();
        System.out.println(tr(
                "=== О ПРОГРАММЕ ===\n"
                        + "Прототип 3 — учебный посимвольно-словесный шифр.\n"
                        + "Слова из словаря заменяются другим словом той же длины и письменности;\n"
                        + "остальные символы крутятся внутри колец своей письменности\n"
                        + "(кириллица, латиница, хирагана, катакана, кандзи, цифры, знаки).\n"
                        + "Японский побуквенный путь помечается символом ヿ.\n"
                        + "Сдвиг = base + счётчик позиции (энигма-степпинг).\n"
                        + "Ключ: SHA-256 от даты (год+месяц+день) или парольная строка (dev-меню).\n"
                        + "Настройки и пароль хранятся в ~/.shifrovka3/config.properties.\n"
                        + "Секретные команды: .clear (.c) ; .contacts ; .dev.config.<пароль> (.dc.<пароль>).\n"
                        + "Это учебная схема, не для реальных секретов.",
                "=== ABOUT ===\n"
                        + "Prototype 3 - an educational char/word substitution cipher.\n"
                        + "Dictionary words are replaced by another word of the same length and script;\n"
                        + "other symbols rotate inside the ring of their script\n"
                        + "(Cyrillic, Latin, hiragana, katakana, kanji, digits, punctuation).\n"
                        + "The Japanese letter path is marked with the symbol ヿ.\n"
                        + "Shift = base + position counter (enigma stepping).\n"
                        + "Key: SHA-256 of the date (year+month+day) or a password string (dev menu).\n"
                        + "Settings and password are stored in ~/.shifrovka3/config.properties.\n"
                        + "Secret commands: .clear (.c) ; .contacts ; .dev.config.<password> (.dc.<password>).\n"
                        + "This is a learning scheme, not for real secrets.",
                "=== 概要 ===\n"
                        + "プロトタイプ3は学習用の文字/単語置換暗号です。\n"
                        + "辞書の単語は同じ長さ・同じ文字体系の別の単語に置換され、\n"
                        + "その他の文字はそれぞれの文字体系の環の中で回転します。\n"
                        + "日本語の文字パスは ヿ でマークされます。\n"
                        + "シフト = base + 位置カウンタ(エニグマ式ステッピング)。\n"
                        + "鍵: 日付(年+月+日)またはパスワード文字列のSHA-256(開発者メニュー)。\n"
                        + "設定とパスワードは ~/.shifrovka3/config.properties に保存されます。\n"
                        + "秘密コマンド: .clear (.c) ; .contacts ; .dev.config.<パスワード> (.dc.<パスワード>)。\n"
                        + "これは学習用の方式であり、実機の秘密には使用しないでください。"));
        System.out.println();
        System.out.print(tr("[Enter] - вернуться: ", "[Enter] - back: ", "[Enter] 戻る: "));
        scanner.nextLine();
        clearScreen();
        printWelcome();
    }

    // ---------- DEV-МЕНЮ ----------
    private static void devMenu(Scanner scanner) {
        clearScreen();                                   // вход в новый слой
        while (true) {
            System.out.println();
            System.out.println(tr("=== DEV-МЕНЮ ===", "=== DEV MENU ===", "=== 開発者メニュー ==="));
            System.out.println(tr("1 - настройка ключа", "1 - key settings", "1 - 鍵の設定"));
            System.out.println(tr("2 - показать конфигурацию", "2 - show configuration", "2 - 設定を表示"));
            System.out.println(tr("3 - сменить пароль dev-меню", "3 - change dev password", "3 - 開発者パスワードを変更"));
            System.out.println(tr("0 - вернуться в главное меню", "0 - back to main menu", "0 - メインメニューに戻る"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> keySettingsMenu(scanner);
                case "2" -> {
                    System.out.println(tr("Режим даты: ", "Date mode: ", "日付モード: ")
                            + (manualDate == null
                            ? tr("авто, сегодня ", "auto, today ", "自動、今日 ") + LocalDate.now()
                            : tr("вручную, ", "manual, ", "手動 ") + manualDate));
                    System.out.println(tr("Тип ключа: ", "Key type: ", "鍵の種類: ")
                            + (keyType == KeyType.DATE ? tr("дата", "date", "日付") : tr("пароль", "password", "パスワード")));
                    System.out.println(tr("base-ключ:       ", "base key:        ", "ベース鍵: ") + currentBase());
                    System.out.println(tr("Русских слов:    ", "Russian words:   ", "ロシア語の単語: ") + countWords(0));
                    System.out.println(tr("Английских слов: ", "English words:   ", "英語の単語: ") + countWords(1));
                    System.out.println(tr("Японских слов:   ", "Japanese words:  ", "日本語の単語: ") + countWords(2));
                    System.out.println(tr("Язык интерфейса: ", "Interface lang:  ", "インターフェース言語: ")
                            + tr("русский", "English", "日本語"));
                }
                case "3" -> {
                    System.out.print(tr("Новый пароль: ", "New password: ", "新しいパスワード: "));
                    String p = scanner.nextLine();
                    if (p.isBlank()) {
                        System.out.println(tr("Пустой пароль не принят.", "Empty password rejected.", "空のパスワードは拒否されました。"));
                    } else {
                        devPassword = p;
                        saveConfig();
                        System.out.println(tr("Пароль обновлён и сохранён.", "Password updated and saved.", "パスワードを更新・保存しました。"));
                    }
                }
                case "0" -> {
                    clearScreen();
                    printWelcome();
                    return;
                }
                default -> System.out.println(tr("Неизвестный пункт", "Unknown item", "不明な項目"));
            }
        }
    }

    // ---------- НАСТРОЙКА КЛЮЧА: ВЫБОР ТИПА ----------
    private static void keySettingsMenu(Scanner scanner) {
        clearScreen();
        while (true) {
            System.out.println();
            System.out.println(tr("=== НАСТРОЙКА КЛЮЧА ===", "=== KEY SETTINGS ===", "=== 鍵の設定 ==="));
            System.out.println(tr("Текущий ключ: ", "Current key: ", "現在の鍵: ")
                    + (keyType == KeyType.DATE ? tr("дата", "date", "日付") : tr("пароль", "password", "パスワード")));
            System.out.println(tr("1 - Дата", "1 - Date", "1 - 日付"));
            System.out.println(tr("2 - Пароль", "2 - Password", "2 - パスワード"));
            System.out.println(tr("0 - назад", "0 - back", "0 - 戻る"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String c = scanner.nextLine().trim();
            if (c.equals("1")) {
                keyType = KeyType.DATE;
                saveConfig();
                dateKeyMenu(scanner);
            } else if (c.equals("2")) {
                keyType = KeyType.PASSWORD;
                saveConfig();
                passwordKeyMenu(scanner);
            } else if (c.equals("0")) {
                clearScreen();
                return;
            } else {
                System.out.println(tr("Неизвестный пункт", "Unknown item", "不明な項目"));
            }
        }
    }

    // ---------- ПОДМЕНЮ: КЛЮЧ-ДАТА ----------
    private static void dateKeyMenu(Scanner scanner) {
        clearScreen();
        while (true) {
            System.out.println();
            System.out.println(tr("=== КЛЮЧ: ДАТА ===", "=== KEY: DATE ===", "=== 鍵: 日付 ==="));
            System.out.println(tr("Статус: ", "Status: ", "状態: ")
                    + (keyType == KeyType.DATE ? tr("активен", "active", "有効") : tr("не активен", "not active", "無効")));
            System.out.println(tr("Дата: ", "Date: ", "日付: ")
                    + (manualDate == null
                    ? tr("авто, сегодня ", "auto, today ", "自動、今日 ") + LocalDate.now()
                    : tr("вручную, ", "manual, ", "手動 ") + manualDate));
            System.out.println(tr("1 - задать дату вручную", "1 - set date manually", "1 - 日付を手動設定"));
            System.out.println(tr("2 - вернуть автоматическую дату", "2 - back to automatic date", "2 - 自動日付に戻す"));
            System.out.println(tr("0 - назад к выбору типа", "0 - back to type selection", "0 - 種類選択に戻る"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String c = scanner.nextLine().trim();
            if (c.equals("1")) {
                System.out.print(tr("Дата (ГГГГ-ММ-ДД или ДД.ММ.ГГГГ): ",
                        "Date (YYYY-MM-DD or DD.MM.YYYY): ", "日付 (YYYY-MM-DD または DD.MM.YYYY): "));
                String s = scanner.nextLine().trim();
                try {
                    manualDate = parseDate(s);
                    saveConfig();
                    System.out.println(tr("Дата установлена: ", "Date set: ", "日付を設定しました: ") + manualDate);
                } catch (Exception e) {
                    System.out.println(tr("Не понял дату, осталось как было.",
                            "Could not parse date, left unchanged.", "日付を解析できません。変更しません。"));
                }
            } else if (c.equals("2")) {
                manualDate = null;
                saveConfig();
                System.out.println(tr("Дата снова автоматическая.", "Date is automatic again.", "日付は再び自動です。"));
            } else if (c.equals("0")) {
                clearScreen();
                return;
            } else {
                System.out.println(tr("Неизвестный пункт", "Unknown item", "不明な項目"));
            }
        }
    }

    // ---------- ПОДМЕНЮ: КЛЮЧ-ПАРОЛЬ ----------
    private static void passwordKeyMenu(Scanner scanner) {
        clearScreen();
        while (true) {
            System.out.println();
            System.out.println(tr("=== КЛЮЧ: ПАРОЛЬ ===", "=== KEY: PASSWORD ===", "=== 鍵: パスワード ==="));
            System.out.println(tr("Статус: ", "Status: ", "状態: ")
                    + (keyType == KeyType.PASSWORD ? tr("активен", "active", "有効") : tr("не активен", "not active", "無効")));
            System.out.println(tr("Пароль: ", "Password: ", "パスワード: ")
                    + (keyPassword.isBlank()
                    ? tr("не задан", "not set", "未設定")
                    : tr("задан (скрыт)", "set (hidden)", "設定済み(非表示)")));
            System.out.println(tr("1 - задать/сменить пароль", "1 - set/change password", "1 - パスワードを設定/変更"));
            System.out.println(tr("2 - показать пароль", "2 - show password", "2 - パスワードを表示"));
            System.out.println(tr("0 - назад к выбору типа", "0 - back to type selection", "0 - 種類選択に戻る"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String c = scanner.nextLine().trim();
            if (c.equals("1")) {
                System.out.print(tr("Новый пароль ключа: ", "New key password: ", "新しい鍵のパスワード: "));
                String p = scanner.nextLine();
                if (p.isBlank()) {
                    System.out.println(tr("Пустой пароль не принят.", "Empty password rejected.", "空のパスワードは拒否されました。"));
                } else {
                    keyPassword = p;
                    saveConfig();
                    System.out.println(tr("Пароль ключа задан.", "Key password set.", "鍵のパスワードを設定しました。"));
                }
            } else if (c.equals("2")) {
                System.out.println(tr("Пароль ключа: ", "Key password: ", "鍵のパスワード: ") + keyPassword);
            } else if (c.equals("0")) {
                clearScreen();
                return;
            } else {
                System.out.println(tr("Неизвестный пункт", "Unknown item", "不明な項目"));
            }
        }
    }

    // ---------- ГЛАВНЫЙ ЦИКЛ ----------
    public static void main(String[] args) {
        loadConfig();
        loadDict(RU_DICT, 0);
        loadDict(EN_DICT, 1);
        loadDict(JP_DICT, 2);
        welcomeKaomoji = KAOMOJI[new Random().nextInt(KAOMOJI.length)];
        Scanner scanner = new Scanner(System.in);
        clearScreen();
        printWelcome();
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println(tr("=== МЕНЮ ===", "=== MENU ===", "=== メニュー ==="));
            System.out.println(tr("1 - зашифровать", "1 - encrypt", "1 - 暗号化"));
            System.out.println(tr("2 - расшифровать", "2 - decrypt", "2 - 復号"));
            System.out.println(tr("3 - язык интерфейса", "3 - interface language", "3 - 界面言語"));
            System.out.println(tr("4 - о программе", "4 - about", "4 - 概要"));
            System.out.println(tr("0 - выход", "0 - exit", "0 - 終了"));
            System.out.println(tr("команды: .contacts .clear (.c) .dev.config.<пароль> (.dc.<пароль>)",
                    "commands: .contacts .clear (.c) .dev.config.<password> (.dc.<password>)",
                    "コマンド: .contacts .clear (.c) .dev.config.<パスワード> (.dc.<パスワード>)"));
            System.out.print(tr("Выбор: ", "Choice: ", "選択: "));
            String line = normalizeCommand(scanner.nextLine().trim());
            if (line.equals(CLEAR_COMMAND)) {
                clearScreen();
                printWelcome();
                continue;
            }
            if (line.equals(CONTACTS_COMMAND)) {
                System.out.println();
                System.out.println(tr("=== КОНТАКТЫ ===", "=== CONTACTS ===", "=== 連絡先 ==="));
                System.out.println(tr("Тг: ", "Tg: ", "Telegram: ") + TELEGRAM);
                System.out.println(tr("Почта: ", "Email: ", "メール: ") + EMAIL);
                System.out.println(tr("GitHub: ", "GitHub: ", "GitHub: ") + GITHUB);
                continue;
            }
            if (line.startsWith(DEV_PREFIX)) {
                if (line.substring(DEV_PREFIX.length()).equals(devPassword)) {
                    devMenu(scanner);
                } else {
                    System.out.println(tr("Команда не распознана.", "Command not recognized.", "コマンドを認識できません。"));
                }
                continue;
            }
            if (line.equals("1")) {
                encryptFlow(scanner);
            } else if (line.equals("2")) {
                decryptFlow(scanner);
            } else if (line.equals("3")) {
                languageMenu(scanner);
            } else if (line.equals("4")) {
                aboutScreen(scanner);
            } else if (line.equals("0")) {
                running = false;
            } else {
                System.out.println(tr("Неизвестный пункт меню", "Unknown menu item", "不明なメニュー項目"));
            }
        }
        saveConfig();
        System.out.println(tr("До встречи!", "See you!", "さようなら!"));
    }
}