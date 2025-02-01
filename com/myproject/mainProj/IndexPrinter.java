package com.myproject.mainProj;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.myproject.utils.StatClasses.InvertedIndexEntry;
import com.myproject.utils.StatClasses.QueryResult;

// **File:** IndexPrinter.java
// **Purpose:** Contains methods for printing inverted index entries by word or document.
class IndexPrinter {

    public static QueryResult getIndexByWord(String word, List<InvertedIndexEntry> invertedIndexEntries)
            throws IOException {
        QueryResult result = new QueryResult();
        result.queryString = "Index entries for word: " + word;
        boolean found = false;
        for (InvertedIndexEntry entry : invertedIndexEntries) {
            if (entry.word.equals(word)) {
                result.retrievedDocIds.add(entry.docId);
                result.docIdToSnippet.put(entry.docId, "Positions: " + entry.positions.toString());
                found = true;
            }
        }
        if (!found) {
            System.out.println("Word '" + word + "' not found in the inverted index.");
        }
        return result;
    }

    public static QueryResult getIndexByDocument(int docId, List<InvertedIndexEntry> invertedIndexEntries)
            throws IOException {
        QueryResult result = new QueryResult();
        result.queryString = "Index entries for Document ID: " + docId;
        boolean found = false;
        Map<String, List<Integer>> wordPositionsMap = new LinkedHashMap<>();

        for (InvertedIndexEntry entry : invertedIndexEntries) {
            if (entry.docId == docId) {
                wordPositionsMap.put(entry.word, entry.positions);
                found = true;
            }
        }

        if (!found) {
            System.out.println("Document ID " + docId + " not found in the inverted index.");
        } else {
            result.retrievedDocIds.add(docId);
            result.wordPositions = wordPositionsMap;
        }
        return result;
    }
}

