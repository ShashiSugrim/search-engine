package com.myproject.mainProj;

import java.util.Arrays;

// **File:** CommandLineParser.java
// **Purpose:** Handles parsing of command line arguments.
class CommandLineParser {

    public String searchType;
    public String searchValue;
    public String printType;
    public String printValue;
    public String outputFilename = "output.json"; // Default output filename
    public boolean doStemming = false;
    public String outputMode = "FILE"; // Default output mode
    public String queryFile;
    public int snippetSize = 5; // Default snippet size

    public CommandLineParser(String[] args) {
        System.out.println("In commandline parser we have these as args: " + Arrays.toString(args));
        parse(args);
    }

    private void parse(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("-SEARCH=WORD")) {
                searchType = "WORD";
                searchValue = parseArgumentValue(args, i, "-SEARCH=WORD");
                i = (searchValue != null) ? i + 1 : i; 
            } else if (arg.startsWith("-SEARCH=DOC")) {
                searchType = "DOC";
                searchValue = parseArgumentValue(args, i, "-SEARCH=DOC");
                i = (searchValue != null) ? i + 1 : i;
            } else if (arg.startsWith("-SEARCH=QUERY")) {
                searchType = "QUERY";
                searchValue = parseArgumentValue(args, i, "-SEARCH=QUERY");
                i = (searchValue != null) ? i + 1 : i;
            } else if (arg.equals("-STEM")) {
                doStemming = true;
            } else if (arg.startsWith("-PRINT_INDEX=WORD")) {
                printType = "WORD";
                printValue = parseArgumentValue(args, i, "-PRINT_INDEX=WORD");
                i = (printValue != null) ? i + 1 : i;
            } else if (arg.startsWith("-PRINT_INDEX=DOC")) {
                printType = "DOC";
                printValue = parseArgumentValue(args, i, "-PRINT_INDEX=DOC");
                i = (printValue != null) ? i + 1 : i;
            } else if (arg.startsWith("-output=")) {
                outputFilename = arg.substring("-output=".length());
            } else if (arg.startsWith("-SNIPPET_SIZE=")) {
                snippetSize = Integer.parseInt(arg.substring("-SNIPPET_SIZE=".length()));
            } else if (arg.startsWith("-GUI=")) {
                String guiOption = arg.substring("-GUI=".length()).toUpperCase();
                outputMode = guiOption.equals("TRUE") ? "GUI" : guiOption.equals("BOTH") ? "BOTH" : "FILE";
            } else if (arg.startsWith("-QUERY_FILE=")) {
                queryFile = arg.substring("-QUERY_FILE=".length());
            } else {
                System.out.println("Unknown argument: " + arg);
            }
        }
        validateOutputMode();
        printUsageIfNeeded();
    }

    private String parseArgumentValue(String[] args, int index, String argName) {
        if (index + 1 < args.length) {
            return args[index + 1];
        } else {
            System.out.println("Error: No value specified for " + argName);
            return null;
        }
    }
    
    private void validateOutputMode() {
        if ((outputMode.equals("FILE") || outputMode.equals("BOTH")) && outputFilename == null) {
            System.out.println("Output file not specified. Using default: output.json");
        }
    }

    private void printUsageIfNeeded() {
        if (queryFile == null && (searchType == null || searchValue == null) && (printType == null || printValue == null)) {
            System.out.println("Usage:");
            System.out.println("  -SEARCH=WORD word OR -SEARCH=DOC docid OR -SEARCH=QUERY \"query string\" [-STEM] [-SNIPPET_SIZE=number] [-GUI=true|false|both] -output=OutputFileName");
            System.out.println("  -PRINT_INDEX=WORD word OR -PRINT_INDEX=DOC docid [-GUI=true|false|both] -output=OutputFileName");
            System.out.println("  -QUERY_FILE=filename [-STEM] [-SNIPPET_SIZE=number] [-GUI=true|false|both] -output=OutputFileName");
        }
    }
    
    public boolean isValid() {
        return !(queryFile == null && (searchType == null || searchValue == null) && (printType == null || printValue == null));
    }
}
