package com.myproject.utils;

import java.awt.Font;
import java.awt.Insets;
import java.io.*;
import java.util.*;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class StatClasses {


    // Define a class to store inverted index entries for easy access
    public static class InvertedIndexEntry {
        public String word;  // Now public
        public int docId;   // Now public
        public List<Integer> positions; // Now public

        public InvertedIndexEntry(String word, int docId, List<Integer> positions) { // Now public
            this.word = word;
            this.docId = docId;
            this.positions = positions;
        }
    }

    // Class to store query results
    public static class QueryResult {
        public String queryString;
        public Set<Integer> retrievedDocIds; // Set of retrieved document IDs
        public Map<Integer, String> docIdToSnippet; // Map of docId to snippets

        public Map<String, Integer> wordFrequencies; // For storing word frequencies (searchByDocument)
        public Map<String, List<Integer>> wordPositions; // For storing word positions (getIndexByDocument)

        public QueryResult() {
            docIdToSnippet = new LinkedHashMap<>();
            retrievedDocIds = new LinkedHashSet<>();
            wordFrequencies = new LinkedHashMap<>();
            wordPositions = new LinkedHashMap<>();
        }
    }
    // Method to write batch results to output file in JSON format
    public static void writeBatchResultsToFile(List<QueryResult> allResults, BufferedWriter writer, Map<Integer, String> documentIdToFilename)
            throws IOException {
        for (QueryResult result : allResults) {
            JSONObject jsonResult = new JSONObject();
            jsonResult.put("query", result.queryString);

            JSONArray docArray = new JSONArray();

            if (result.wordFrequencies != null && !result.wordFrequencies.isEmpty()) {
                // Output word frequencies
                for (Map.Entry<String, Integer> entry : result.wordFrequencies.entrySet()) {
                    JSONObject frequencyObject = new JSONObject();
                    frequencyObject.put("word", entry.getKey());
                    frequencyObject.put("frequency", entry.getValue());
                    docArray.add(frequencyObject);
                }
            } else if (result.wordPositions != null && !result.wordPositions.isEmpty()) {
                // Output word positions
                for (Map.Entry<String, List<Integer>> entry : result.wordPositions.entrySet()) {
                    JSONObject positionObject = new JSONObject();
                    positionObject.put("word", entry.getKey());
                    positionObject.put("docId", result.retrievedDocIds.iterator().next());
                    positionObject.put("positions", entry.getValue());
                    docArray.add(positionObject);
                }
            } else if (result.retrievedDocIds.isEmpty()) {
                // No results case
                JSONObject noResultObject = new JSONObject();
                noResultObject.put("message", "No results found.");
                docArray.add(noResultObject);
            } else {
                // Regular search results
                for (Integer docId : result.retrievedDocIds) {
                    String docName = documentIdToFilename.get(docId);
                    String snippet = result.docIdToSnippet.get(docId);
                    JSONObject docObject = new JSONObject();
                    docObject.put("docName", docName);
                    docObject.put("snippet", snippet);
                    docArray.add(docObject);
                }
            }

            jsonResult.put("results", docArray);
            System.out.println(jsonResult.toJSONString());
            writer.write(jsonResult.toJSONString() + "\n");
        }
    }

    // Method to display batch results in GUI
    public static void displayBatchResultsInGUI(List<QueryResult> allResults, Map<Integer, String> documentIdToFilename) {
        // Create the main JFrame
        JFrame frame = new JFrame("Search Results");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800); // Default size to fit both sections

        // Left panel for snippets
        JPanel snippetsPanel = new JPanel();
        snippetsPanel.setLayout(new BoxLayout(snippetsPanel, BoxLayout.Y_AXIS)); // Vertical alignment
        snippetsPanel.setBorder(BorderFactory.createTitledBorder("Results")); // Adjusted title

        // Right panel for document information (if needed)
        JPanel documentsPanel = new JPanel();
        documentsPanel.setLayout(new BoxLayout(documentsPanel, BoxLayout.Y_AXIS)); // Vertical alignment
        documentsPanel.setBorder(BorderFactory.createTitledBorder("Documents")); // Panel title

        if (allResults.isEmpty()) {
            JLabel noResultsLabel = new JLabel("No results found.");
            snippetsPanel.add(noResultsLabel);
            documentsPanel.add(noResultsLabel);
        } else {
            int overallIndex = 1; // Numbering across all results
            for (QueryResult result : allResults) {
                // Add the query text as a header for both panels
                JLabel queryLabelLeft = new JLabel(result.queryString);
                queryLabelLeft.setFont(new Font("SansSerif", Font.BOLD, 14)); // Bold font for query
                queryLabelLeft.setAlignmentX(JLabel.LEFT_ALIGNMENT); // Align it to the left
                snippetsPanel.add(queryLabelLeft);

                if (result.wordFrequencies != null && !result.wordFrequencies.isEmpty()) {
                    // For searchByDocument
                    for (Map.Entry<String, Integer> entry : result.wordFrequencies.entrySet()) {
                        JLabel entryLabel = new JLabel("Word: " + entry.getKey() + ", Frequency: " + entry.getValue());
                        entryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                        snippetsPanel.add(entryLabel);
                    }
                } else if (result.wordPositions != null && !result.wordPositions.isEmpty()) {
                    // For getIndexByDocument
                    // Write header line
                    JLabel headerLabel = new JLabel("Word,DocumentID,Positions");
                    headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
                    headerLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                    snippetsPanel.add(headerLabel);

                    for (Map.Entry<String, List<Integer>> entry : result.wordPositions.entrySet()) {
                        String word = entry.getKey();
                        List<Integer> positions = entry.getValue();
                        JLabel entryLabel = new JLabel(
                                word + "," + result.retrievedDocIds.iterator().next() + "," + positions.toString());
                        entryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                        snippetsPanel.add(entryLabel);
                    }
                } else if (result.retrievedDocIds.isEmpty()) {
                    JLabel noResultsSnippetLabel = new JLabel("No results found for this query.");
                    noResultsSnippetLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                    snippetsPanel.add(noResultsSnippetLabel);
                } else {
                    // For other searches (e.g., QUERY)
                    for (Integer docId : result.retrievedDocIds) {
                        String snippet = result.docIdToSnippet.get(docId);
                        String docName = documentIdToFilename.get(docId);

                        // Left panel: Snippet with number
                        JPanel snippetRow = new JPanel();
                        snippetRow.setLayout(new BoxLayout(snippetRow, BoxLayout.X_AXIS));
                        snippetRow.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                        JLabel snippetIndexLabel = new JLabel(overallIndex + ". ");
                        JTextArea snippetText = new JTextArea(snippet);
                        snippetText.setLineWrap(true);
                        snippetText.setWrapStyleWord(true);
                        snippetText.setEditable(false);
                        snippetText.setBackground(snippetsPanel.getBackground());
                        snippetText.setMargin(new Insets(0, 2, 0, 2)); // Tighten margins

                        snippetRow.add(snippetIndexLabel);
                        snippetRow.add(snippetText);
                        snippetsPanel.add(snippetRow);

                        // Right panel: Document with number
                        JLabel documentEntryLabel = new JLabel(
                                overallIndex + ". Document ID: " + docId + ", Document Name: " + docName);
                        documentEntryLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
                        documentsPanel.add(documentEntryLabel);

                        overallIndex++;
                    }
                }
                snippetsPanel.add(Box.createVerticalStrut(5)); // Add a small spacing between queries
            }
        }

        // If documentsPanel is empty, we can skip adding it
        JComponent mainComponent;
        if (documentsPanel.getComponentCount() == 0) {
            mainComponent = new JScrollPane(snippetsPanel);
        } else {
            // Vertical split between snippets and documents
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(snippetsPanel), new JScrollPane(documentsPanel));
            splitPane.setDividerLocation(600); // Default location to evenly split the window
            splitPane.setResizeWeight(0.5); // Equal distribution of space
            mainComponent = splitPane;
        }

        // Add main component to the frame
        frame.getContentPane().add(mainComponent);

        // Display the window
        frame.setLocationRelativeTo(null); // Center the window on the screen
        SwingUtilities.invokeLater(() -> frame.setVisible(true)); // Show the GUI
    }
}