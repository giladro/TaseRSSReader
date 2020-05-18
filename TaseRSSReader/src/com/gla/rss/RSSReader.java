package com.gla.rss;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RSSReader {
    static final String DB_URL = "jdbc:mysql://localhost:3306/rss?useUnicode=yes&characterEncoding=UTF-8";
    static final String USER = "gilad";
    static final String PASS = "jrha2020";
    private static final String TASE_RSS_XML = "https://www.magna.isa.gov.il/rss.xml";
    private static final String LAST = "/home/gilad/IdeaProjects/TaseRSSReader/Resources/LAST.txt";
    private static final int READ_EVERY_SECOND = 60;
    private static final SymbolsReader symbolsReader = new SymbolsReader();

    public static String debugGetURLContent(String p_sURL) {
        URL oURL;
        URLConnection oConnection;
        BufferedReader oReader;
        String sLine;
        StringBuilder sbResponse;
        String sResponse = null;
        try {
            oURL = new URL(p_sURL);
            oConnection = oURL.openConnection();
            oReader = new BufferedReader(new InputStreamReader(oConnection.getInputStream()));
            sbResponse = new StringBuilder();

            while ((sLine = oReader.readLine()) != null) {
                sbResponse.append(sLine);
            }

            sResponse = sbResponse.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sResponse;
    }

    public static void main(String[] args) {
        try {
            symbolsReader.readSymbols();
            while (true) {
                RSSReader rssReader = new RSSReader();
                List<Article> articles = rssReader.readNewArticles();
                rssReader.saveArticles(articles);
                rssReader.findReports();
                Thread.sleep(READ_EVERY_SECOND * 1000);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void saveArticles(List<Article> articles) throws Exception {
        Connection connection = DriverManager.getConnection(DB_URL, USER, PASS);
        PreparedStatement preparedStatement = connection.prepareStatement("insert into rss.articles values (default, ?, ?, ?, ? , ?, ?, ?)");
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss");
        for (Article article : articles) {
            preparedStatement.setString(1, article.getTitle());
            preparedStatement.setString(2, article.getLink());
            preparedStatement.setString(3, article.getDescription());
            preparedStatement.setString(4, article.getCompanyName());
            preparedStatement.setString(5, article.getCompanyFullName());
            preparedStatement.setLong(6, article.getMarketCap());
            DateTime dateTime = dateTimeFormatter.parseDateTime(article.getPublicationDate().replace(" GMT", ""));
            preparedStatement.setLong(7, dateTime.getMillis());
            preparedStatement.executeUpdate();
        }
        connection.close();
    }

    private void findReports() throws Exception {
        // Read articles and find a companies that reported too many times in a short period
        // Or via a direct query to the DB
        Connection connection = DriverManager.getConnection(DB_URL, USER, PASS);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from rss.articles");
        writeResultSet(resultSet);
        connection.close();
    }

    private void writeResultSet(ResultSet resultSet) throws SQLException {
        while (resultSet.next()) {
            // TODO: handle hebrew in store
            String description = resultSet.getString("description");
            if(description.contains("קורונה")) {
                String companyName = resultSet.getString("company_name");
                if(!companyName.isEmpty()) {
                    System.out.println("Company: " + companyName + ", Description: " + description);
                }
            }
        }
    }

    private List<Article> readNewArticles() throws Exception {
        //String content = debugGetURLContent(TASE_RSS_XML);
        URL url = new URL(TASE_RSS_XML);
        InputStream stream = url.openStream();
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(stream);
        System.out.println("----------------------------");
        NodeList nodeList = document.getElementsByTagName("item");
        List<Article> articles = new ArrayList<>();
        String savedLink = Files.readString(Paths.get(LAST), StandardCharsets.US_ASCII).trim();
        String lastLink = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            //System.out.println("\nCurrent Element :" + node.getNodeName());
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Article article = new Article();
                String link = element.getElementsByTagName("link").item(0).getTextContent();
                if (link.equals(savedLink)) break;
                if (lastLink == null) {
                    lastLink = link;
                }
                article.setLink(link);
                article.setTitle(element.getElementsByTagName("title").item(0).getTextContent());
                article.setDescription(element.getElementsByTagName("description").item(0).getTextContent());
                article.setPublicationDate(element.getElementsByTagName("pubDate").item(0).getTextContent());
                Company company = symbolsReader.getCompanyFromText(article.getTitle());
                if (company == null && !article.getDescription().isEmpty()) {
                    company = symbolsReader.getCompanyFromText(article.getDescription());
                }
                if (company != null) {
                    article.setCompanyName(company.getName());
                    article.setCompanyFullName(company.getFullName());
                    article.setMarketCap(company.getMarketCap());
                }
                articles.add(article);
                System.out.println(article);
            }
        }

        if (lastLink != null) {
            PrintWriter printWriter = new PrintWriter(LAST);
            printWriter.println(lastLink);
            printWriter.close();
        }

        return articles;
    }
}