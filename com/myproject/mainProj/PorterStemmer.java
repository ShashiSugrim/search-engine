package com.myproject.mainProj;
import java.io.*;
import java.util.*;


public class PorterStemmer {

    // Dictionary to store word and its corresponding stem
    private static Map<String, String> dictionary = new HashMap<>();

    public static void main(String[] args) {
        String invertedIndexFileName = "inverted_index.txt";
        String stemmingDictFileName = "stemming_dictionary.txt";

        // Load existing stemming dictionary if it exists
        File stemmingDictFile = new File(stemmingDictFileName);
        if (stemmingDictFile.exists()) {
            loadStemmingDictionary(stemmingDictFileName);
        }

        // Process the inverted index file
        try (BufferedReader br = new BufferedReader(new FileReader(invertedIndexFileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Extract the word (assumed to be the first item before the first comma)
                String word = line.split(",")[0];

                // If the word is not already in the dictionary, stem it and add to dictionary
                if (!dictionary.containsKey(word)) {
                    String stem = stem(word);
                    dictionary.put(word, stem);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Save the updated dictionary to the stemming dictionary file
        saveStemmingDictionary(stemmingDictFileName);

        // Print the dictionary (Optional: For testing purposes)
        dictionary.forEach((word, stem) -> System.out.println("Word: " + word + " -> Stem: " + stem));
    }

    // Method to load the stemming dictionary from a file
    private static void loadStemmingDictionary(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Each line is assumed to be in the format "word,stem"
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    String word = parts[0];
                    String stem = parts[1];
                    dictionary.put(word, stem);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading stemming dictionary: " + e.getMessage());
        }
    }

    // Method to save the stemming dictionary to a file
    private static void saveStemmingDictionary(String fileName) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
            for (Map.Entry<String, String> entry : dictionary.entrySet()) {
                String word = entry.getKey();
                String stem = entry.getValue();
                bw.write(word + "," + stem);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing stemming dictionary: " + e.getMessage());
        }
    }

    // Porter's stemming algorithm implementation
    public static String stem(String word) {
        PorterStemmer stemmer = new PorterStemmer();
        return stemmer.process(word);
    }

    // Processes a word with Porter Stemming rules
    public String process(String word) {
        word = word.toLowerCase();
        if (word.length() < 3) return word;

        word = step1a(word);
        word = step1b(word);
        word = step1c(word);
        word = step2(word);
        word = step3(word);
        word = step4(word);
        word = step5a(word);
        word = step5b(word);
        return word;
    }

    // Step 1a
    private String step1a(String word) {
        if (word.endsWith("sses")) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "i";
        } else if (word.endsWith("ss")) {
            return word;
        } else if (word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    // Step 1b
    private String step1b(String word) {
        boolean flag = false;

        if (word.endsWith("eed")) {
            String stem = word.substring(0, word.length() - 3);
            if (measure(stem) > 0) {
                word = word.substring(0, word.length() - 1); // Remove 'd'
            }
        } else if ((word.endsWith("ed") && containsVowel(word.substring(0, word.length() - 2)))) {
            word = word.substring(0, word.length() - 2);
            flag = true;
        } else if ((word.endsWith("ing") && containsVowel(word.substring(0, word.length() - 3)))) {
            word = word.substring(0, word.length() - 3);
            flag = true;
        }

        if (flag) {
            if (word.endsWith("at") || word.endsWith("bl") || word.endsWith("iz")) {
                word = word + "e";
            } else if (endsWithDoubleConsonant(word) && !word.endsWith("l") && !word.endsWith("s") && !word.endsWith("z")) {
                word = word.substring(0, word.length() - 1);
            } else if (measure(word) == 1 && cvc(word)) {
                word = word + "e";
            }
        }
        return word;
    }

    // Step 1c
    private String step1c(String word) {
        if (word.endsWith("y")) {
            String stem = word.substring(0, word.length() - 1);
            if (containsVowel(stem)) {
                word = stem + "i";
            }
        }
        return word;
    }

    // Step 2
    private String step2(String word) {
        Map<String, String> suffixes = new LinkedHashMap<>();

        suffixes.put("ational", "ate");
        suffixes.put("tional", "tion");
        suffixes.put("enci", "ence");
        suffixes.put("anci", "ance");
        suffixes.put("izer", "ize");
        suffixes.put("abli", "able");
        suffixes.put("alli", "al");
        suffixes.put("entli", "ent");
        suffixes.put("eli", "e");
        suffixes.put("ousli", "ous");
        suffixes.put("ization", "ize");
        suffixes.put("ation", "ate");
        suffixes.put("ator", "ate");
        suffixes.put("alism", "al");
        suffixes.put("iveness", "ive");
        suffixes.put("fulness", "ful");
        suffixes.put("ousness", "ous");
        suffixes.put("aliti", "al");
        suffixes.put("iviti", "ive");
        suffixes.put("biliti", "ble");

        for (Map.Entry<String, String> entry : suffixes.entrySet()) {
            String suffix = entry.getKey();
            String replacement = entry.getValue();

            if (word.endsWith(suffix)) {
                String stem = word.substring(0, word.length() - suffix.length());
                if (measure(stem) > 0) {
                    word = stem + replacement;
                    return word;
                }
            }
        }
        return word;
    }

    // Step 3
    private String step3(String word) {
        Map<String, String> suffixes = new LinkedHashMap<>();

        suffixes.put("icate", "ic");
        suffixes.put("ative", "");
        suffixes.put("alize", "al");
        suffixes.put("iciti", "ic");
        suffixes.put("ical", "ic");
        suffixes.put("ful", "");
        suffixes.put("ness", "");

        for (Map.Entry<String, String> entry : suffixes.entrySet()) {
            String suffix = entry.getKey();
            String replacement = entry.getValue();

            if (word.endsWith(suffix)) {
                String stem = word.substring(0, word.length() - suffix.length());
                if (measure(stem) > 0) {
                    word = stem + replacement;
                    return word;
                }
            }
        }
        return word;
    }

    // Step 4
    private String step4(String word) {
        String[] suffixes = {
                "al", "ance", "ence", "er", "ic", "able", "ible",
                "ant", "ement", "ment", "ent", "ion", "ou", "ism",
                "ate", "iti", "ous", "ive", "ize"
        };
        for (String suffix : suffixes) {
            if (word.endsWith(suffix)) {
                String stem = word.substring(0, word.length() - suffix.length());
                if (measure(stem) > 1) {
                    if (suffix.equals("ion")) {
                        if (stem.endsWith("s") || stem.endsWith("t")) {
                            word = stem;
                        }
                    } else {
                        word = stem;
                    }
                    return word;
                }
            }
        }
        return word;
    }

    // Step 5a
    private String step5a(String word) {
        if (word.endsWith("e")) {
            String stem = word.substring(0, word.length() - 1);
            int m = measure(stem);
            if (m > 1 || (m == 1 && !cvc(stem))) {
                word = stem;
            }
        }
        return word;
    }

    // Step 5b
    private String step5b(String word) {
        if (measure(word) > 1 && endsWithDoubleConsonant(word) && word.endsWith("l")) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }

    // Helper methods

    // Measure the number of VC sequences
    private int measure(String word) {
        int count = 0;
        int i = 0;
        int length = word.length();

        while (i < length && isConsonant(word, i)) {
            i++;
        }
        while (i < length) {
            while (i < length && !isConsonant(word, i)) {
                i++;
            }
            while (i < length && isConsonant(word, i)) {
                i++;
            }
            count++;
        }
        return count;
    }

    // Check if the word contains a vowel
    private boolean containsVowel(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (!isConsonant(word, i)) {
                return true;
            }
        }
        return false;
    }

    // Check if a character at position i is a consonant
    private boolean isConsonant(String word, int i) {
        char c = word.charAt(i);
        if ("aeiou".indexOf(c) != -1) {
            return false;
        }
        if (c == 'y') {
            if (i == 0) {
                return true;
            } else {
                return !isConsonant(word, i - 1);
            }
        }
        return true;
    }

    // Check if the word ends with a double consonant
    private boolean endsWithDoubleConsonant(String word) {
        int length = word.length();
        if (length >= 2) {
            if (word.charAt(length - 1) == word.charAt(length - 2)) {
                return isConsonant(word, length - 1);
            }
        }
        return false;
    }

    // Check if the word ends with a consonant-vowel-consonant, where the final consonant is not w, x, or y
    private boolean cvc(String word) {
        int length = word.length();
        if (length >= 3) {
            boolean c1 = isConsonant(word, length - 1);
            boolean v = !isConsonant(word, length - 2);
            boolean c2 = isConsonant(word, length - 3);
            char lastChar = word.charAt(length - 1);
            if (c1 && v && c2 && lastChar != 'w' && lastChar != 'x' && lastChar != 'y') {
                return true;
            }
        }
        return false;
    }
}