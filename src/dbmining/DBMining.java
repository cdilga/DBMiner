package dbmining;

import java.io.BufferedWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CacheMap;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class DBMining implements Runnable {

    public ArrayList<String> rows = new ArrayList<String>();
    public String query;
    public ArrayList<String> Query;

    // "How many discoveries Martin has made after 1990?";
    public static String question;
    public static ArrayList<String> Question;
    public static ConcurrentHashMap<String, Float[]> vectors = new ConcurrentHashMap<String, Float[]>();

    public Statement stmt;

    //Input Question.
    public static void main(String[] args) throws SQLException {
        if (args.length == 1) {
            question = args[0];
        } else {
            System.out.println("Some inputs is missing!");
            return;
        }
        try {
            loadNumberBatch();
            Question = extractQuestionColumns();
        } catch (SQLException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            MyService = new ServerSocket(1506);
        } catch (IOException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }

        //------------------------------------------------
        DBMining db = new DBMining();
        int threads = 8;
        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            t[i] = new Thread(db);
            t[i].start();
        }
        //------------------------------------------------
    }
    public static ServerSocket MyService;

    @Override
    public void run() {
        try {

            while (true) {
                Socket mySocket = MyService.accept();
                System.out.println("Connected");
                DataInputStream input = new DataInputStream(mySocket.getInputStream());
                BufferedReader r = new BufferedReader(new InputStreamReader(input));
                String st = r.readLine();
                System.out.println(st);
                if (st.equals("DIE")) {
                    break;
                }
                this.setQuery(st);
                String response = "0\n";
                try {
                    response = Double.toString(this.getFitness()) + "\n";
                } catch (Exception ex) {
                    System.out.println(ex.getStackTrace().toString());
                    System.out.println("Query was not valid. Using zero fitness instead!");
                    //System.in.read();
                }

                DataOutputStream output = new DataOutputStream(mySocket.getOutputStream());
                output.writeUTF(response);
                //output.writeUTF("hello");
                output.flush();
                output.close();
                System.out.println("Fitness : " + response);
                mySocket.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setQuery(String quer) {
        query = quer;
    }

    private DBMining() throws SQLException {
        stmt = OpenConnectionMYSQL("species");

    }

    private DBMining(String quer) throws SQLException {
        query = quer;
        stmt = OpenConnectionMYSQL("species");

    }

    public Float[] getVector(String word) {
        return vectors.get(word);
    }

    public double getFitness() throws SQLException {
        Query = extractQueryColumns(query);
        System.out.println("---------------------------------------------------");
        System.out.println("Query: " + Query);
        System.out.println("Question: " + Question);
        return Fitness(query, getColumnsRelevance());
    }

    public ArrayList<ArrayList<relevance>> getRowsRelevance() throws SQLException {
        ArrayList<ArrayList<relevance>> str = new ArrayList<ArrayList<relevance>>();
        ResultSet result = getResultSet(query);
        if (result == null) {
            System.out.println("Result set is null.");
        }
        while (result.next()) {
            ArrayList<relevance> temp = new ArrayList<relevance>();
            for (int i = 1; i <= Query.size(); i++) {
                relevance rel = new relevance();
                rel.word = result.getString(i);
                rel.value = cellRelevanceScore(result.getString(i));
                temp.add(rel);
            }
            str.add(temp);
        }
        return str;
    }

    public class relevance {

        String word;
        Double value;
    }

    public double cellRelevanceScore(String cell) {
        DescriptiveStatistics temp = new DescriptiveStatistics();
        for (int i = 0; i < Question.size(); i++) {
            double sim = 0;
            try {
                sim = cosineSimilarity(Question.get(i), cell);
            } catch (Exception Ex) {
                System.out.println("calculating sim failed.");
            }
            temp.addValue(sim);
        }
        return temp.getMax();
    }

    public ArrayList<relevance> getColumnsRelevance() throws SQLException {
        ArrayList<DescriptiveStatistics> desc = new ArrayList<DescriptiveStatistics>();
        for (int j = 0; j < Query.size(); j++) {
            DescriptiveStatistics temp = new DescriptiveStatistics();
            for (int i = 0; i < Question.size(); i++) {
                double sim = cosineSimilarity(Question.get(i), Query.get(j));
                //System.out.println(Question.get(i) + "," + Query.get(j) + " : " + sim);
                temp.addValue(sim);
            }
            desc.add(temp);
        }
        ArrayList<relevance> str = new ArrayList<>();
        for (int j = 0; j < Query.size(); j++) {
            relevance rel = new relevance();
            rel.word = Query.get(j);
            rel.value = desc.get(j).getMean();
            str.add(rel);
        }
        //System.out.println("Query: " + str.);
        return str;
    }

    //================================================================================================
    public static ArrayList<String> extractQuestionColumns() {
        HashMap<String, String> QuestionColumns;
        ArrayList<String> QuestionColumnsList = new ArrayList<>();

        QuestionColumns = parseText(question);
        Iterator it = QuestionColumns.keySet().iterator();
        while (it.hasNext()) {
            QuestionColumnsList.add((String) it.next());
        }
        ArrayList<String> temp = new ArrayList<>();
        for (int i = 0; i < QuestionColumnsList.size(); i++) {
            String tag = QuestionColumns.get(QuestionColumnsList.get(i));
            System.out.println(QuestionColumnsList.get(i) + " " + tag);
            if (tag.contains("CD")) {
                int num = Integer.parseInt(QuestionColumnsList.get(i));
                if (num >= 1700 && num <= 2020) {
                    temp.add("year");
                }
            } else if (tag.contains("NN")) {
                temp.add(QuestionColumnsList.get(i));
            }
        }
        return temp;
    }

    public double Fitness(String query, ArrayList<relevance> colrel) throws SQLException {
        //System.out.println(colrel);
        ArrayList<ArrayList<relevance>> rowsrel = getRowsRelevance();
        //System.out.println(rowsrel);
        DescriptiveStatistics stat = new DescriptiveStatistics();
        for (int i = 0; i < rowsrel.size(); i++) {
            double temp = 0;
            ArrayList<relevance> rowrel = rowsrel.get(i);
            for (int j = 0; j < colrel.size(); j++) {
                double d1 = colrel.get(j).value;
                double d2 = rowrel.get(j).value;
                temp += d1 * d2;
            }
            stat.addValue(temp);
        }
        Double out = stat.getMean();
        return out.isNaN() ? 0.0 : out;
    }

    public ArrayList<String> extractQueryColumns(String query) throws SQLException {
        ResultSet result = getResultSet(query);
        System.out.println("Result set ready.");
        ArrayList<String> ResultsColumns = new ArrayList<>();
        ResultSetMetaData rsmd = result.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String name = rsmd.getColumnName(i);
            ResultsColumns.add(name);
        }
        return ResultsColumns;
    }

    public ResultSet getResultSet(String query) throws SQLException {
        return stmt.executeQuery(query);
    }

    public static HashMap<String, String> parseText(String text) {
        HashMap<String, String> output = new HashMap();
        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);

        // run all Annotators on this text
        pipeline.annotate(document);

        // these are all the sentences in this document
        // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
        List<CoreMap> sentences = document.get(SentencesAnnotation.class);

        for (CoreMap sentence : sentences) {
            // traversing the words in the current sentence
            // a CoreLabel is a CoreMap with additional token-specific methods
            for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
                // this is the text of the token
                String word = token.get(TextAnnotation.class);
                // this is the POS tag of the token
                String pos = token.get(PartOfSpeechAnnotation.class);
                output.put(word, pos);
                // this is the NER label of the token
                String ne = token.get(NamedEntityTagAnnotation.class);
            }

            // this is the parse tree of the current sentence
            Tree tree = sentence.get(TreeAnnotation.class);

            // this is the Stanford dependency graph of the current sentence
            SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
        }

        // This is the coreference link graph
        // Each chain stores a set of mentions that link to each other,
        // along with a method for getting the most representative mention
        // Both sentence and token offsets start at 1!
        Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
        return output;
    }

    public static Statement OpenConnectionMYSQL(String Dataset) throws SQLException {
        String url = "jdbc:mysql://localhost:3306/" + Dataset;
        String username = "root";
        String password = "example";
        Connection connection = DriverManager.getConnection(url, username, password);
        Statement stmt = connection.createStatement();
        return (Statement) stmt;
    }

    //================================================================================================
    public static void loadNumberBatch() throws SQLException {
        System.out.println("Loading the numberbatch started!");
        Statement stmt = OpenConnectionMYSQL("conceptnet");
        String sql = "select * from conceptnet.numberbatch;";
        ResultSet rs = stmt.executeQuery(sql);
        //**************************************

        while (rs.next()) {
            String[] vecs = rs.getString(2).split(" ");
            if (vecs.length != 300) {
                System.out.println("Vecs less than 300, Vecs: " + vecs.length + " for " + rs.getString(1));
                continue;
            }
            Float[] numVecs = new Float[vecs.length];
            for (int i = 0; i < vecs.length; i++) {
                numVecs[i] = Float.parseFloat(vecs[i]);
            }
            vectors.put(rs.getString(1), numVecs);
        }
        System.out.println("Loading the numberbatch finished!");
    }

    public double cosineSimilarity(String word1, String word2) {
        Float[] wordAvec = null;
        Float[] wordBvec = null;
        try {
            wordAvec = getVector(word1.toLowerCase());
            wordBvec = getVector(word2.toLowerCase());
        } catch (Exception Ex) {
            System.out.println("Failed getting vector: " + Ex.getMessage());
            return 0;
        }
        if (wordAvec == null || wordBvec == null) {
           //System.out.println("The word " + word1 + " or " + word2 + " is not in conceptnet.");
            return 0;
        }
     
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < wordAvec.length; i++) {
            dotProduct += wordAvec[i] * wordBvec[i];
            //TODO Fix strings
            normA += Math.pow(wordAvec[i], 2);
            normB += Math.pow(wordBvec[i], 2);
        }
        double temp = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

        //return ((double) Math.acos(temp) / Math.PI) * 100;
        return temp;
    }

    public double eulideanSimilarity(String word1, String word2) {
        Float[] wordAvec = null;
        Float[] wordBvec = null;
        try {
            wordAvec = getVector(word1.toLowerCase());
            wordBvec = getVector(word2.toLowerCase());
        } catch (Exception Ex) {
            System.out.println("Failed getting vector: " + Ex.getMessage());
            return 0;
        }
        if (wordAvec == null || wordBvec == null) {
            System.out.println("The word " + word1 + " or " + word2 + " is not in conceptnet.");
            return 0;
        }
        
        double diff_square_sum = 0.0;
        for (int i = 0; i < wordAvec.length; i++) {
            diff_square_sum += (wordAvec[i] - wordBvec[i]) * (wordAvec[i] - wordBvec[i]);
        }
        return Math.sqrt(diff_square_sum);
    }
    //================================================================================================
}
