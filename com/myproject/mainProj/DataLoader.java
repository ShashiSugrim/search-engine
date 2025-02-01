// **File:** DataLoader.java
// **Purpose:** Handles loading of data from files (stoplist, stemming dictionary, document ID map, inverted index).
package com.myproject.mainProj;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.HashSet;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.myproject.utils.StatClasses.InvertedIndexEntry;


class DataLoader {

    public static HashSet<String> loadStoplist(String filename) {
        HashSet<String> stoplist = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stoplist.add(line.trim().toLowerCase());
            }
        } catch (IOException e) {
            System.out.println("Error reading stoplist file: " + e.getMessage());
        }
        return stoplist;
    }

    public static Map<String, String> loadStemmingDictionary(String fileName) {
        Map<String, String> stemmingDictionary = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    stemmingDictionary.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading stemming dictionary: " + e.getMessage());
        }
        return stemmingDictionary;
    }
    
    public static void saveStemmingDictionary(Map<String, String> stemmingDictionary, String fileName) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
            for (Map.Entry<String, String> entry : stemmingDictionary.entrySet()) {
                bw.write(entry.getKey() + "," + entry.getValue());
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing stemming dictionary: " + e.getMessage());
        }
    }

    public static Map<Integer, String> loadDocumentIdMap(String fileName) {
        Map<Integer, String> documentIdToFilename = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line = br.readLine(); 
            while ((line = br.readLine()) != null) {
                int lastCommaIndex = line.lastIndexOf(',');
                if (lastCommaIndex != -1) {
                    String documentName = line.substring(0, lastCommaIndex);
                    String docIdStr = line.substring(lastCommaIndex + 1).trim();
                    documentIdToFilename.put(Integer.parseInt(docIdStr), documentName);
                } else {
                    System.err.println("Invalid line in document_id_map.txt: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading document ID map: " + e.getMessage());
        }
        return documentIdToFilename;
    }

    public static void loadInvertedIndex(String filename, boolean doStemming,
            HashMap<String, HashMap<Integer, List<Integer>>> invertedIndex,
            List<InvertedIndexEntry> invertedIndexEntries, Map<String, Set<Integer>> variantToDocIds,
            Map<String, String> stemmingDictionary) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            reader.readLine(); // skip first line that is header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                String word = parts[0];
                int docId = Integer.parseInt(parts[1]);
                List<Integer> positions = parsePositions(parts[2]);

                // If stemming is enabled, stem the word
                String stem = (doStemming) ? (stemmingDictionary.containsKey(word) ? stemmingDictionary.get(word) : PorterStemmer.stem(word)) : word;

                // Add to invertedIndex
                invertedIndex.computeIfAbsent(stem, k -> new HashMap<>())
                        .computeIfAbsent(docId, k -> new ArrayList<>())
                        .addAll(positions);

                // Store the entry for printing purposes
                invertedIndexEntries.add(new InvertedIndexEntry(stem, docId, positions));

                // Build the variantToDocIds mapping
                Set<String> variants = new HashSet<>();
                variants.add(stem);

                for (String variant : variants) {
                    Set<Integer> docIds = variantToDocIds.getOrDefault(variant, new HashSet<>());
                    docIds.add(docId);
                    variantToDocIds.put(variant, docIds);
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading inverted index: " + e.getMessage());
        }
    }

    private static List<Integer> parsePositions(String positionsStr) {
        List<Integer> positions = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(positionsStr);
        while (matcher.find()) {
            positions.add(Integer.parseInt(matcher.group()));
        }
        return positions;
    }

    public static List<String> readQueriesFromFile(String queryFile) throws IOException {
        List<String> queries = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(queryFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    queries.add(line.trim());
                }
            }
        }
        return queries;
    }
}

