package com.gla.rss;

import java.io.File;
import java.util.*;

public class SymbolsReader {
    private static final char DEFAULT_SEPARATOR = '\t';
    private static final char DEFAULT_QUOTE = '"';
    private static final String RESOURCES_DIR = "/home/gilad/IdeaProjects/TaseRSSReader/Resources/";
    private static final String TASE_SYMBOLS = RESOURCES_DIR + "TASE.tsv";
    private static final String TASE_DESC = RESOURCES_DIR + "TASE_DESC.tsv";
    private static final String TASE_MORE = RESOURCES_DIR + "TASE_MORE.tsv";

    private final Map<String, Company> nameToCompany = new HashMap<>();

    public static void main(String[] args) {
        try {
            SymbolsReader symbolsReader = new SymbolsReader();
            symbolsReader.readSymbols();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static List<String> parseLine(String cvsLine) {
        return parseLine(cvsLine, DEFAULT_SEPARATOR, DEFAULT_QUOTE);
    }

    private static List<String> parseLine(String cvsLine, char separators, char customQuote) {

        List<String> result = new ArrayList<>();

        //if empty, return!
        if (cvsLine == null || cvsLine.isEmpty()) {
            return result;
        }

        if (customQuote == ' ') {
            customQuote = DEFAULT_QUOTE;
        }

        if (separators == ' ') {
            separators = DEFAULT_SEPARATOR;
        }

        StringBuffer curVal = new StringBuffer();
        boolean inQuotes = false;
        boolean startCollectChar = false;
        boolean doubleQuotesInColumn = false;

        char[] chars = cvsLine.toCharArray();

        for (char ch : chars) {

            if (inQuotes) {
                startCollectChar = true;
                if (ch == customQuote) {
                    inQuotes = false;
                    doubleQuotesInColumn = false;
                } else {

                    //Fixed : allow "" in custom quote enclosed
                    if (ch == '\"') {
                        if (!doubleQuotesInColumn) {
                            curVal.append(ch);
                            doubleQuotesInColumn = true;
                        }
                    } else {
                        curVal.append(ch);
                    }

                }
            } else {
                if (ch == customQuote) {

                    inQuotes = true;

                    //Fixed : allow "" in empty quote enclosed
                    if (chars[0] != '"' && customQuote == '\"') {
                        curVal.append('"');
                    }

                    //double quotes in column will hit this!
                    if (startCollectChar) {
                        curVal.append('"');
                    }

                } else if (ch == separators) {

                    result.add(curVal.toString());

                    curVal = new StringBuffer();
                    startCollectChar = false;

                } else if (ch == '\r') {
                    //ignore LF characters
                    continue;
                } else if (ch == '\n') {
                    //the end, break!
                    break;
                } else {
                    curVal.append(ch);
                }
            }
        }

        result.add(curVal.toString());

        return result;
    }

    public void readSymbols() throws Exception {
        readCSV(TASE_SYMBOLS);
        readCSV(TASE_DESC);
        readCSV(TASE_MORE);
        // Fill
        for (Map.Entry<String, Company> entry : nameToCompany.entrySet()) {
            Company company = entry.getValue();
            if (company.getFullName().isEmpty()) {
                System.out.println("Missing full name for " + company.getName());
            }
        }
        /*for (Map.Entry<String, Company> entry : nameToCompany.entrySet()) {
            Company company = entry.getValue();
            if(!company.getFullName().isEmpty()) {
                System.out.println(company.getName() + " ==> " + company.getFullName());
            }
        }*/
    }

    public Company getCompanyFromText(String text) {
        // TODO: several companies can match the text if the message refers to both
        text = text.replaceAll("\"", "");
        String[] textParts = text.trim().split("\\s+");
        Company bestMatchCompany = null;
        double bestRatioMatch = 0;
        int bestMatch = 0;
        for (Map.Entry<String, Company> entry : nameToCompany.entrySet()) {
            Company company = entry.getValue();
            String name = company.getFullName().isEmpty() ? company.getName() : company.getFullName();
            String[] nameParts = name.split("\\s+");
            int match = 0;
            for (String namePart : nameParts) {
                for (String textPart : textParts) {
                    if (textPart.equals(namePart)) {
                        match++;
                        break;
                    }
                }
            }
            double ratioMatch = ((double) match / nameParts.length) * 100;
            if (ratioMatch > 0 && (ratioMatch > bestRatioMatch || (ratioMatch == bestRatioMatch && match > bestMatch))) {
                bestMatchCompany = company;
                bestRatioMatch = ratioMatch;
                bestMatch = match;
            }
        }
        if (bestRatioMatch < 75.0) {
            return null;
        }
        return bestMatchCompany;
    }

    private void handleLine(String csvFile, List<String> line) {
        if (csvFile.equals(TASE_SYMBOLS)) {
            Company company = new Company();
            company.setSymbol(line.get(1).trim());
            company.setName(line.get(0).trim());
            company.setMarketCap(Integer.parseInt(line.get(17).trim()));
            company.setFullName("");
            nameToCompany.put(company.getName(), company);
        } else if (csvFile.equals(TASE_DESC)) {
            String name = line.get(0).trim();
            if (nameToCompany.containsKey(name)) {
                String fullName = line.get(7);
                fullName = fullName.replace("בעמ", "").trim();
                nameToCompany.get(name).setFullName(fullName);
            }
        } else { //TASE_MORE
            String name = line.get(0).trim();
            if (nameToCompany.containsKey(name)) {
                String fullName = line.get(1);
                fullName = fullName.replace("בעמ", "").trim();
                nameToCompany.get(name).setFullName(fullName);
            }
        }
    }

    private void readCSV(String csvFile) throws Exception {
        Scanner scanner = new Scanner(new File(csvFile));
        boolean skip = true;
        while (scanner.hasNext()) {
            try {
                String nextLine = scanner.nextLine();
                if (skip) {
                    skip = false;
                    continue;
                }
                List<String> line = parseLine(nextLine);
                handleLine(csvFile, line);
                //System.out.println(symbol);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        scanner.close();
    }
}
