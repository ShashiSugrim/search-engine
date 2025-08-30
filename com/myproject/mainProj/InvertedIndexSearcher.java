package com.myproject.mainProj;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import com.myproject.utils.StatClasses;
import com.myproject.utils.StatClasses.InvertedIndexEntry;
import com.myproject.utils.StatClasses.QueryResult;

// **File:** InvertedIndexSearcher.java (Main class)
// **Purpose:** Contains the main method and orchestrates the search process.
public class InvertedIndexSearcher {

    private static HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex = new HashMap<>();
    private static List<InvertedIndexEntry> invertedIndexEntries = new ArrayList<>();
    private static Map<String, String> stemmingDictionary = new HashMap<>();
    private static Map<String, Set<Integer>> variantToDocIds = new HashMap<>();
    public static Map<Integer, String> documentIdToFilename = new HashMap<>();
    private static HashSet<String> stoplist = new HashSet<>();

    public static void main(String[] args) {
        // System.out.println("Current Working Directory: " + System.getProperty("user.dir"));

        CommandLineParser parser = new CommandLineParser(args);

        if (!parser.isValid()) {
            return; // Exit if command line arguments are invalid
        }

        // Load necessary data
        String baseDir = parser.fileDirectory;
        // System.out.println("base directory in inverted index searcher is " + baseDir);

        stoplist = DataLoader.loadStoplist(Paths.get(baseDir, "generated_stoplist.txt").toString());
        // System.out.println("stoplist is " + stoplist);
        stemmingDictionary = DataLoader
                .loadStemmingDictionary(Paths.get(baseDir, "stemming_dictionary.txt").toString());
        documentIdToFilename = DataLoader.loadDocumentIdMap(Paths.get(baseDir, "document_id_map.txt").toString());

        DataLoader.loadInvertedIndex(Paths.get(baseDir, "inverted_index.txt").toString(), parser.doStemming,
                invertedIndex, invertedIndexEntries, variantToDocIds, stemmingDictionary);

        // Perform the search or print operation
        try {
            List<QueryResult> allResults = new ArrayList<>();

            if (parser.queryFile != null) {
                // Batch query processing
                List<String> queryLines = DataLoader.readQueriesFromFile(parser.queryFile);
                allResults.addAll(processBatchQueries(queryLines, parser.doStemming, parser.snippetSize));
                // Output all results for batch query
                outputBatchResults(allResults, parser.outputMode, parser.outputFilename);
            } else if (parser.searchType != null && parser.searchValue != null) {
                // Single query processing
                QueryResult result = processSingleQuery(parser.searchType, parser.searchValue, parser.doStemming,
                        parser.snippetSize);
                if (result != null) {
                    allResults.add(result);
                    // Output results for single query
                    outputBatchResults(allResults, parser.outputMode, parser.outputFilename);
                }
                // System.out.println("Search completed.");
            } else if (parser.printType != null && parser.printValue != null) {
                // Print index entries
                QueryResult result = processPrintRequest(parser.printType, parser.printValue);
                if (result != null) {
                    allResults.add(result);
                    // Output results for print request
                    outputBatchResults(allResults, parser.outputMode, parser.outputFilename);
                }
                System.out.println("Inverted index entries have been processed.");
            }
            DataLoader.saveStemmingDictionary(stemmingDictionary, "stemming_dictionary.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<QueryResult> processBatchQueries(List<String> queryLines, boolean doStemming, int snippetSize) {
        List<QueryResult> allResults = new ArrayList<>();
        int queryNumber = 1;
        for (String queryLine : queryLines) {
            String[] queryParts = queryLine.trim().split("\\s+", 2);
            if (queryParts.length < 2) {
                System.out.println("Invalid query line: " + queryLine);
                continue;
            }
            String queryCommand = queryParts[0];
            String queryArgument = queryParts[1];

            String searchType = null;
            String searchValue = null;
            if (queryCommand.equalsIgnoreCase("-SEARCH=WORD")) {
                searchType = "WORD";
                searchValue = queryArgument;
            } else if (queryCommand.equalsIgnoreCase("-SEARCH=DOC")) {
                searchType = "DOC";
                searchValue = queryArgument;
            } else if (queryCommand.equalsIgnoreCase("-SEARCH=QUERY")) {
                searchType = "QUERY";
                searchValue = queryArgument;
            } else {
                System.out.println("Unknown query command: " + queryCommand);
                continue;
            }

            QueryResult result = processSingleQuery(searchType, searchValue, doStemming, snippetSize);
            if (result != null) {
                result.queryString = "query " + queryNumber + ": " + queryLine;
                allResults.add(result);
            }
            queryNumber++;
        }
        return allResults;
    }

    public static QueryResult processSingleQuery(String searchType, String searchValue, boolean doStemming,
                                                 int snippetSize) {
        try {
            if (searchType.equals("WORD")) {
                return SearchProcessor.searchByWord(searchValue.toLowerCase(), doStemming, invertedIndex,
                        documentIdToFilename, snippetSize, stemmingDictionary);
            } else if (searchType.equals("DOC")) {
                return SearchProcessor.searchByDocument(Integer.parseInt(searchValue), invertedIndex);
            } else if (searchType.equals("QUERY")) {
                return SearchProcessor.searchByQuery(searchValue, doStemming, invertedIndex, stoplist,
                        documentIdToFilename, snippetSize, stemmingDictionary);
            }
        } catch (IOException e) {
            System.out.println("Error during search: " + e.getMessage());
        }
        return null;
    }

    private static QueryResult processPrintRequest(String printType, String printValue) {
        try {
            if (printType.equals("WORD")) {
                return IndexPrinter.getIndexByWord(printValue.toLowerCase(), invertedIndexEntries);
            } else if (printType.equals("DOC")) {
                return IndexPrinter.getIndexByDocument(Integer.parseInt(printValue), invertedIndexEntries);
            }
        } catch (IOException e) {
            System.out.println("Error during print request: " + e.getMessage());
        }
        return null;
    }

    private static void outputBatchResults(List<QueryResult> allResults, String outputMode, String outputFilename)
            throws IOException {
        if (outputMode.equals("FILE") || outputMode.equals("BOTH")) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilename))) {
                StatClasses.writeBatchResultsToFile(allResults, writer, documentIdToFilename);
            } catch (IOException e) {
                System.out.println("Error writing to output file: " + e.getMessage());
            }
        }
        if (outputMode.equals("GUI") || outputMode.equals("BOTH")) {
            StatClasses.displayBatchResultsInGUI(allResults, documentIdToFilename);
        }
    }
}
