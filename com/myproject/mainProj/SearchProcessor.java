package com.myproject.mainProj;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import com.myproject.utils.StatClasses.QueryResult;

// **File:** SearchProcessor.java
// **Purpose:** Contains methods for searching by word, document, and query.
class SearchProcessor {

    public static QueryResult searchByWord(String word, boolean doStemming,
            HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex, Map<Integer, String> documentIdToFilename,
            int snippetSize, Map<String, String> stemmingDictionary) throws IOException {
        QueryResult result = new QueryResult();

        // If stemming is enabled, stem





        // If stemming is enabled, stem the word
        String stemmedWord = word;
        if (doStemming) {
            stemmedWord = stemmingDictionary.containsKey(word) ? stemmingDictionary.get(word) : PorterStemmer.stem(word);
        }
        result.queryString = "Search by word: " + word;

        if (invertedIndex.containsKey(stemmedWord)) {
            for (Map.Entry<Integer, List<Integer>> entry : invertedIndex.get(stemmedWord).entrySet()) {
                int docId = entry.getKey();
                result.retrievedDocIds.add(docId);

                // Generate snippet
                List<Integer> positions = entry.getValue();
                int earliestPosition = Collections.min(positions);
                String snippet = SnippetGenerator.generateSnippet(documentIdToFilename, docId, earliestPosition, snippetSize);
                result.docIdToSnippet.put(docId, "Snippet: " + (snippet != null ? snippet : "Unable to generate snippet."));
            }
        } else {
            System.out.println("Word '" + word + "' not found in the index.");
        }
        return result;
    }

    public static QueryResult searchByDocument(int docId, HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex)
            throws IOException {
        QueryResult result = new QueryResult();
        result.queryString = "Document ID: " + docId;
        boolean found = false;
        Map<String, Integer> wordFreqMap = new LinkedHashMap<>();

        for (Map.Entry<String, HashMap<Integer, List<Integer>>> entry : invertedIndex.entrySet()) {
            String word = entry.getKey();
            if (entry.getValue().containsKey(docId)) {
                int frequency = entry.getValue().get(docId).size();
                wordFreqMap.put(word, frequency);
                found = true;
            }
        }

        if (!found) {
            System.out.println("No words found for Document ID " + docId + ".");
        } else {
            result.retrievedDocIds.add(docId);
            result.wordFrequencies = wordFreqMap;
        }
        return result;
    }

    public static QueryResult searchByQuery(String query, boolean doStemming,
            HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex, HashSet<String> stoplist,
            Map<Integer, String> documentIdToFilename, int snippetSize, Map<String, String> stemmingDictionary) throws IOException {
        QueryResult result = new QueryResult();
        query = query.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        query = query.replaceAll("\\s+", " ");
        result.queryString = "Query: " + query;

        String[] words = query.split("\\s+");
        List<String> filteredWords = new ArrayList<>();
        for (String word : words) {
            word = word.toLowerCase();
            if (!stoplist.contains(word)) {
                filteredWords.add(word);
            }
        }

        if (filteredWords.isEmpty()) {
            System.out.println("All query words are stopwords.");
            return result;
        }

        Set<String> queryTerms = new HashSet<>();
        for (String word : filteredWords) {
            String term = (doStemming) ? (stemmingDictionary.containsKey(word) ? stemmingDictionary.get(word) : PorterStemmer.stem(word)) : word;
            queryTerms.add(term);
        }

        Set<Integer> resultDocIds = null;
        for (String term : queryTerms) {
            if (!invertedIndex.containsKey(term)) {
                resultDocIds = new HashSet<>();
                break;
            }
            Set<Integer> docIds = invertedIndex.get(term).keySet();
            if (resultDocIds == null) {
                resultDocIds = new HashSet<>(docIds);
            } else {
                resultDocIds.retainAll(docIds);
            }
            if (resultDocIds.isEmpty()) {
                break;
            }
        }

        if (resultDocIds != null && !resultDocIds.isEmpty()) {
            for (Integer docId : resultDocIds) {
                result.retrievedDocIds.add(docId);
                String snippet = SnippetGenerator.generateSnippetForQuery(invertedIndex, documentIdToFilename, docId, queryTerms, snippetSize);
                result.docIdToSnippet.put(docId, "Snippet: " + (snippet != null ? snippet : "Unable to generate snippet."));
            }
        } else {
            System.out.println("No documents contain all the words in the query.");
        }

        return result;
    }
}
