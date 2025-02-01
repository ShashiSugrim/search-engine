package com.myproject.mainProj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

// **File:** SnippetGenerator.java
// **Purpose:** Contains methods for generating snippets.
class SnippetGenerator {

    public static String generateSnippet(Map<Integer, String> documentIdToFilename, int docId, int positionInDocument, int snippetSize) {
        String documentContent = readDocumentContent(documentIdToFilename, docId);
        if (documentContent != null) {
            documentContent = documentContent.toLowerCase();
            documentContent = documentContent.replaceAll("<[^>]*>", " "); 
            documentContent = documentContent.replaceAll("[^a-zA-Z0-9\\s]", " ");

            Scanner scanner = new Scanner(documentContent);
            List<String> words = new ArrayList<>();
            while (scanner.hasNext()) {
                words.add(scanner.next().trim());
            }
            scanner.close();

            int position = positionInDocument - 1;
            int totalWords = words.size();
            int start = Math.max(0, position - snippetSize);
            int end = Math.min(totalWords - 1, position + snippetSize);

            StringBuilder snippetBuilder = new StringBuilder();
            for (int i = start; i <= end; i++) {
                snippetBuilder.append(words.get(i)).append(" ");
            }
            return snippetBuilder.toString().trim();
        } else {
            return null;
        }
    }

    public static String generateSnippetForQuery(HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex, Map<Integer, String> documentIdToFilename, int docId, Set<String> queryTerms, int snippetSize) {
        TreeMap<Integer, String> positionToWordMap = new TreeMap<>();
        for (String term : queryTerms) {
            HashMap<Integer, List<Integer>> docIdToPositions = invertedIndex.get(term);
            if (docIdToPositions != null) {
                List<Integer> positions = docIdToPositions.get(docId);
                if (positions != null) {
                    for (Integer pos : positions) {
                        positionToWordMap.put(pos, term);
                    }
                }
            }
        }

        if (!positionToWordMap.isEmpty()) {
            Integer earliestPosition = positionToWordMap.firstKey();
            return generateSnippet(documentIdToFilename, docId, earliestPosition, snippetSize);
        } else {
            return null;
        }
    }

    private static String readDocumentContent(Map<Integer, String> documentIdToFilename, int docId) {
        String filename = documentIdToFilename.get(docId);
        if (filename == null) {
            System.err.println("Error: Document ID " + docId + " not found in document_id_map.txt");
            return null;
        }
        try {
            return new String(Files.readAllBytes(Paths.get(filename)));
        } catch (IOException e) {
            System.err.println("Error reading document " + filename + ": " + e.getMessage());
            return null;
        }
    }
}
