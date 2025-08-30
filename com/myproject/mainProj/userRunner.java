package com.myproject.mainProj;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import com.myproject.utils.StatClasses.QueryResult;

public class userRunner {

    public static void main(String[] args) {
        System.out.println("userrunnner is running");

        if (args.length == 0 || args[0] == null || args[0].trim().isEmpty()) {
            System.err.println("Usage: java userRunner <baseDir>");
            return;
        }

        boolean doStemming = false;
        int snippetSize = 5;

        // Clean quotes from baseDir arg
        String baseDir = args[0].replace("'", "").replace("\"", "");
        System.out.println("Base dir: " + baseDir);

        // === Load data into LOCAL structures that we'll pass to SearchProcessor ===
        HashSet<String> stoplist = DataLoader.loadStoplist(Paths.get(baseDir, "generated_stoplist.txt").toString());

        Map<String, String> stemmingDictionary =
                DataLoader.loadStemmingDictionary(Paths.get(baseDir, "stemming_dictionary.txt").toString());

        // Load id -> filename map, then rewrite to absolute paths under baseDir
        Map<Integer, String> documentIdToFilename =
                DataLoader.loadDocumentIdMap(Paths.get(baseDir, "document_id_map.txt").toString());

        // If your HTML files are directly in baseDir, this resolves to <baseDir>/<filename>.
        // (If they live in a subfolder like "docs", change Paths.get(baseDir) to Paths.get(baseDir, "docs").)
        Map<Integer, String> fixedDocMap = new HashMap<>();
        for (Map.Entry<Integer, String> e : documentIdToFilename.entrySet()) {
            String abs = Paths.get(baseDir).resolve(e.getValue()).toString();
            fixedDocMap.put(e.getKey(), abs);
        }
        documentIdToFilename = fixedDocMap;

        HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex = new HashMap<>();
        List<com.myproject.utils.StatClasses.InvertedIndexEntry> invertedIndexEntries = new ArrayList<>();
        Map<String, Set<Integer>> variantToDocIds = new HashMap<>();

        DataLoader.loadInvertedIndex(
                Paths.get(baseDir, "inverted_index.txt").toString(),
                doStemming,
                invertedIndex,
                invertedIndexEntries,
                variantToDocIds,
                stemmingDictionary
        );

        // === Interactive query loop ===
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your query text (type 'exit' to quit).");
        System.out.println("Examples:");
        System.out.println("  biology");
        System.out.println("  machine learning retrieval");
        System.out.println("  doc 12          (lookup by document id)");
        System.out.println("  word banana     (lookup exact word entry)");
        System.out.println("  -SEARCH=QUERY neural networks   (old style, still supported)");
        System.out.println();

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim();
            if (userInput.isEmpty()) continue;

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("Exiting...");
                break;
            }

            String searchType;
            String searchValue;

            // Back-compat: old "-SEARCH=..." format
            if (userInput.toLowerCase(Locale.ROOT).startsWith("-search=")) {
                String[] parts = userInput.split("\\s+", 2);
                if (parts.length < 2) {
                    System.out.println("Invalid query format. Use '-SEARCH=TYPE value'.");
                    continue;
                }
                String cmd = parts[0].toUpperCase(Locale.ROOT);
                searchValue = parts[1];

                if (cmd.equals("-SEARCH=WORD")) {
                    searchType = "WORD";
                } else if (cmd.equals("-SEARCH=DOC")) {
                    searchType = "DOC";
                } else if (cmd.equals("-SEARCH=QUERY")) {
                    searchType = "QUERY";
                } else {
                    System.out.println("Unknown query command: " + cmd);
                    continue;
                }

                // Friendly aliases:
            } else if (userInput.toLowerCase(Locale.ROOT).startsWith("doc ")) {
                searchType = "DOC";
                searchValue = userInput.substring(4).trim();
            } else if (userInput.toLowerCase(Locale.ROOT).startsWith("word ")) {
                searchType = "WORD";
                searchValue = userInput.substring(5).trim();

                // Default: whole line is a free-text QUERY
            } else {
                searchType = "QUERY";
                searchValue = userInput;
            }

            try {
                QueryResult result = null;

                switch (searchType) {
                    case "WORD":
                        result = SearchProcessor.searchByWord(
                                searchValue.toLowerCase(Locale.ROOT),
                                doStemming,
                                invertedIndex,
                                documentIdToFilename,
                                snippetSize,
                                stemmingDictionary
                        );
                        break;

                    case "DOC":
                        int docId;
                        try {
                            docId = Integer.parseInt(searchValue.trim());
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid document id: " + searchValue);
                            continue;
                        }
                        result = SearchProcessor.searchByDocument(docId, invertedIndex);
                        break;

                    case "QUERY":
                        result = SearchProcessor.searchByQuery(
                                searchValue,
                                doStemming,
                                invertedIndex,
                                stoplist,
                                documentIdToFilename,
                                snippetSize,
                                stemmingDictionary
                        );
                        break;

                    default:
                        System.out.println("Unknown search type: " + searchType);
                        continue;
                }

                if (result == null || result.retrievedDocIds == null || result.retrievedDocIds.isEmpty()) {
                    System.out.println("No results found for: " + searchValue);
                    continue;
                }

                System.out.println("Query: " + searchValue);
                for (Integer id : result.retrievedDocIds) {
                    String fname = documentIdToFilename.getOrDefault(id, "(unknown)");
                    System.out.println("Document ID: " + id + "  |  " + fname);

                    if (result.docIdToSnippet != null) {
                        String snip = result.docIdToSnippet.get(id);
                        if (snip != null && !snip.isEmpty()) {
                            System.out.println("Snippet: " + snip);
                        } else {
                            System.out.println("Snippet: Unable to generate snippet.");
                        }
                    }
                }

            } catch (IOException ioe) {
                System.out.println("Error processing query: " + ioe.getMessage());
            } catch (Exception e) {
                System.out.println("Unexpected error: " + e.getMessage());
            }
        }

        scanner.close();
    }
}
