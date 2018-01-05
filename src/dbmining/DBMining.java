package dbmining;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.hcoref.data.CorefChain;
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
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class DBMining implements Runnable {

    public ArrayList<String> rows = new ArrayList<String>();
    public String query;
    public ArrayList<String> QueryWords;
    public ArrayList<relevance> queryColumnsRel;

    // "How many discoveries Martin has made after 1990?";
    public static String question = "How many discoveries Martin has made after 1990?";
    public static String groundTruthQuery;
    public static ArrayList<String> QuestionWords;
    public static ConcurrentHashMap<StringKey, Double> SimiliartiesHashMap = new ConcurrentHashMap<StringKey, Double>();
    public static ConcurrentHashMap<String, Float[]> vectors = new ConcurrentHashMap<String, Float[]>();
    public Statement stmt;
    public static Boolean storeRelevanceScores = false;
    public static ArrayList<String> SQL = new ArrayList<String>();
    public static ConcurrentHashMap<String, String> WORDSTAGS = new ConcurrentHashMap<>();
    public static HashMap<String, Boolean> testedQueries = new HashMap<String, Boolean>();
    public static BufferedWriter bw;
    public static Table<String, String, String> ALL = HashBasedTable.create();
    public static Table<String, String, String> GT = HashBasedTable.create();

    public class StringKey {

        public String str1;
        public String str2;

        public StringKey(String s1, String s2) {
            str1 = s1;
            str2 = s2;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && obj instanceof StringKey) {
                StringKey s = (StringKey) obj;
                return str1.equals(s.str1) && str2.equals(s.str2);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (str1 + str2).hashCode();
        }
    }

    //Input Question.
    public static void main(String[] args) throws SQLException, IOException {
        bw = new BufferedWriter(new FileWriter("coverages.csv"));
        if (args.length == 2) {
            question = args[0];
            groundTruthQuery = args[1];
        } else {
            System.out.println("Some of the inputs are missing!");
            return;
        }
        try {
            loadNumberBatch();
            QuestionWords = extractQuestionWords();
        } catch (SQLException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            MyService = new ServerSocket(1506);
        } catch (IOException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Question is: " + question);
        //System.out.println("Now enter the best query for this question:");
        //Scanner scanner = new Scanner(System.in);
        //String groundTruthQuery = scanner.nextLine();
        System.out.println("The ground truth has a fitness of: " + getQueryFitness(groundTruthQuery));
        storeRelevanceScores = true;
        System.out.println("The fitness for all the tables is: " + getQueryFitness("SELECT * FROM species.insectdiscoveries;"));
        storeRelevanceScores = false;
        saveRelevanceScores();
        System.out.println("Press any key to continue...");
        System.in.read();
        System.out.println("Coverage of ground truth vs all table is: " + getQueryCoverage(groundTruthQuery, "SELECT * FROM species.insectdiscoveries;"));
        //=========================================================================================
        DBMining dbb = new DBMining();
        ResultSet result = dbb.getResultSet("SELECT * FROM species.insectdiscoveries;");
        ResultSetMetaData rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                ALL.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        result = dbb.getResultSet(groundTruthQuery);
        rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                GT.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        //=========================================================================================
        //-----------------------------------------------------------------------------------------
        int threads = 8;
        DBMining[] db = new DBMining[8];
        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            db[i] = new DBMining();
            t[i] = new Thread(db[i]);
            t[i].start();
        }
        //-----------------------------------------------------------------------------------------
    }

    public static void saveRelevanceScores() throws SQLException {
        Statement s = OpenConnectionMYSQL("species");
        s.executeUpdate("truncate table insectdiscoveriesrel;");
        for (int i = 0; i < SQL.size(); i++) {
            //System.out.println(SQL.get(i));
            s.executeUpdate(SQL.get(i));
        }
        s.close();
    }

    public static double getQueryCoverage(String groundTruthQuery, String query) throws SQLException {
        query = query.replaceAll("SELECT ", "SELECT `ID`,");
        //=========================================================================================
        Table<String, String, String> ALL = HashBasedTable.create();
        DBMining db = new DBMining();
        ResultSet result = db.getResultSet("SELECT * FROM species.insectdiscoveries;");
        ResultSetMetaData rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                ALL.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        Table<String, String, String> GT = HashBasedTable.create();
        result = db.getResultSet(groundTruthQuery);
        rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                GT.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        Table<String, String, String> Q = HashBasedTable.create();
        result = db.getResultSet(query);
        rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                Q.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        Iterator it = ALL.cellSet().iterator();
        double FP = 0;
        double FN = 0;
        double N = 0;
        while (it.hasNext()) {
            Cell<String, String, String> c = (Cell) it.next();
            String rowKey = c.getRowKey();
            String colKey = c.getColumnKey();
            if (GT.contains(rowKey, colKey) && Q.contains(rowKey, colKey)) {
            } else if (!GT.contains(rowKey, colKey) && Q.contains(rowKey, colKey)) {
                FP++;
            } else if (GT.contains(rowKey, colKey) && !Q.contains(rowKey, colKey)) {
                FN++;
            } else if (!GT.contains(rowKey, colKey) && !Q.contains(rowKey, colKey)) {
            }
            N++;
        }
        //=========================================================================================
        return 1 - ((double) FP / N) - (2 * (double) FN / N);
    }

    public double getQueryCoverage() throws SQLException {
        //=========================================================================================
        Table<String, String, String> QUERY = HashBasedTable.create();
        ResultSet result = this.getResultSet(query);
        ResultSetMetaData rsmd = result.getMetaData();
        while (result.next()) {
            String ID = result.getString(1);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                QUERY.put(ID, name, result.getString(name));
            }
        }
        //=========================================================================================
        Iterator it = ALL.cellSet().iterator();
        double FP = 0;
        double FN = 0;
        double N = ALL.size();
        while (it.hasNext()) {
            Cell<String, String, String> c = (Cell) it.next();
            String rowKey = c.getRowKey();
            String colKey = c.getColumnKey();
            if (GT.contains(rowKey, colKey) && QUERY.contains(rowKey, colKey)) {
            } else if (!GT.contains(rowKey, colKey) && QUERY.contains(rowKey, colKey)) {
                FP++;
            } else if (GT.contains(rowKey, colKey) && !QUERY.contains(rowKey, colKey)) {
                FN++;
            } else if (!GT.contains(rowKey, colKey) && !QUERY.contains(rowKey, colKey)) {
            }
        }
        //=========================================================================================
        return 1 - ((double) FP / N) - (2 * (double) FN / N);
    }

    public static double getQueryFitness(String query) throws SQLException {
        DBMining db = new DBMining(query);
        return db.getFitness();
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
                double fitness = 0;

                query = query.replaceAll("SELECT ", "SELECT `ID`,");
                double coverage = this.getQueryCoverage();
                System.out.println("Fitness : " + fitness + "---" + "Coverage: " + coverage);
                if (!testedQueries.containsKey(this.query)) {
                    synchronized (this) {
                        bw.write("\"" + this.query + "\",\"" + fitness + "\",\"" + coverage + "\"\n");
                        bw.flush();
                    }
                }
                testedQueries.put(this.query, true);

                try {
                    fitness = this.getFitness();
                    response = Double.toString(fitness) + "\n" + coverage + "\n";
                } catch (Exception ex) {
                    System.out.println(ex.getStackTrace().toString());
                    System.out.println("Query was not valid. Using zero fitness instead!");
                    //System.in.read();
                }
                
                //send the response to the client and close the stream as well as the socket.
                DataOutputStream output = new DataOutputStream(mySocket.getOutputStream());
                output.writeUTF(response);
                output.flush();
                output.close();
                mySocket.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(DBMining.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setQuery(String quer) {
        query = quer;
    }

    private DBMining() throws SQLException {
        stmt = OpenConnectionMYSQL("species");
        queryColumnsRel = new ArrayList<>();
    }

    private DBMining(String quer) throws SQLException {
        query = quer;
        stmt = OpenConnectionMYSQL("species");
        queryColumnsRel = new ArrayList<>();
    }

    public Float[] getVector(String word) {
        return vectors.get(word);
    }

    public double getFitness() throws SQLException {
        QueryWords = extractQueryColumns(query);
        System.out.println("---------------------------------------------------");
        System.out.println("Query: " + QueryWords);
        System.out.println("Question: " + QuestionWords);
        queryColumnsRel = getColumnsRelevance();
        return Fitness(query, queryColumnsRel);
    }

    public ArrayList<ArrayList<relevance>> getRowsRelevance() throws SQLException {
        ArrayList<ArrayList<relevance>> str = new ArrayList<ArrayList<relevance>>();
        ResultSet result = getResultSet(query);
        if (result == null) {
            System.out.println("Result set is null.");
        }
        while (result.next()) {
            ArrayList<relevance> temp = new ArrayList<relevance>();
            String[] t = new String[QueryWords.size()];
            for (int i = 1; i <= QueryWords.size(); i++) {
                relevance rel = new relevance();
                rel.word = result.getString(i + 1);
                rel.value = cellRelevanceScore(rel.word);
                //t[i - 1] = rel.word + ":" + (rel.value + queryColumnsRel.get(i - 1).value);
                t[i - 1] = rel.word + ":" + rel.value;
                temp.add(rel);
            }
            str.add(temp);
            if (storeRelevanceScores) {
                SQL.add(createInsertQuery(t));
            }
        }
        return str;
    }

    public ArrayList<relevance> getColumnsRelevance() throws SQLException {
        ArrayList<DescriptiveStatistics> desc = new ArrayList<DescriptiveStatistics>();
        for (int j = 0; j < QueryWords.size(); j++) {
            DescriptiveStatistics temp = new DescriptiveStatistics();
            for (int i = 0; i < QuestionWords.size(); i++) {
                double sim = cosineSimilarity(QuestionWords.get(i), QueryWords.get(j));
                //System.out.println(Question.get(i) + "," + Query.get(j) + " : " + sim);
                temp.addValue(sim);
            }
            desc.add(temp);
        }
        ArrayList<relevance> str = new ArrayList<>();
        String[] t = new String[QueryWords.size()];
        for (int j = 0; j < QueryWords.size(); j++) {
            relevance rel = new relevance();
            rel.word = QueryWords.get(j);
            rel.value = desc.get(j).getMean();
            t[j] = rel.word + ":" + rel.value;
            str.add(rel);
        }
        if (storeRelevanceScores) {
            SQL.add(createInsertQuery(t));
        }
        return str;
    }

    public String createInsertQuery(String[] t) throws SQLException {
        String sql = "Insert into " + "species.insectdiscoveriesrel";
        sql += "( "
                + "`" + QueryWords.get(0) + "`" + ","
                + "`" + QueryWords.get(1) + "`" + ","
                + "`" + QueryWords.get(2) + "`" + ","
                + "`" + QueryWords.get(3) + "`" + ","
                + "`" + QueryWords.get(4) + "`" + ","
                + "`" + QueryWords.get(5) + "`" + ","
                + "`" + QueryWords.get(6) + "`" + ","
                + "`" + QueryWords.get(7) + "`" + ","
                + "`" + QueryWords.get(8) + "`" + ","
                + "`" + QueryWords.get(9) + "`" + ","
                + "`" + QueryWords.get(10) + "`" + ","
                + "`" + QueryWords.get(11) + "`" + ","
                + "`" + QueryWords.get(12) + "`" + ","
                + "`" + QueryWords.get(13) + "`" + ","
                + "`" + QueryWords.get(14) + "`" + ","
                + "`" + QueryWords.get(15) + "`" + ","
                + "`" + QueryWords.get(16) + "`" + ","
                + "`" + QueryWords.get(17) + "`" + ","
                + "`" + QueryWords.get(18) + "`" + ","
                + "`" + QueryWords.get(19) + "`" + ","
                + "`" + QueryWords.get(20) + "`" + ","
                + "`" + QueryWords.get(21) + "`"
                + " )"
                + "Values ( "
                + " '" + t[0] + "', "
                + " '" + t[1] + "', "
                + " '" + t[2] + "', "
                + " '" + t[3] + "', "
                + " '" + t[4] + "', "
                + " '" + t[5] + "', "
                + " '" + t[6] + "', "
                + " '" + t[7] + "', "
                + " '" + t[8] + "', "
                + " '" + t[9] + "', "
                + " '" + t[10] + "', "
                + " '" + t[11] + "', "
                + " '" + t[12] + "', "
                + " '" + t[13] + "', "
                + " '" + t[14] + "', "
                + " '" + t[15] + "', "
                + " '" + t[16] + "', "
                + " '" + t[17] + "', "
                + " '" + t[18] + "', "
                + " '" + t[19] + "', "
                + " '" + t[20] + "', "
                + " '" + t[21] + "' "
                + " );";
        return sql;
    }

    public class relevance {

        String word;
        Double value;
    }

    public double cellRelevanceScore(String cell) {
        DescriptiveStatistics temp = new DescriptiveStatistics();
        for (int i = 0; i < QuestionWords.size(); i++) {
            double sim = 0;
            try {
                sim = cosineSimilarity(QuestionWords.get(i), cell);
            } catch (Exception Ex) {
                System.out.println("calculating sim failed.");
            }
            temp.addValue(sim);
        }
        return temp.getMean();
    }

    public class TableCell {

        int ID;
        String Name;
        String Value;
        Double Relevance;

        public TableCell(int id, String name, String value) {
            ID = id;
            Name = name;
            Value = value;
        }

        public void setcellRelevanceScore() {
            DescriptiveStatistics temp = new DescriptiveStatistics();
            for (int i = 0; i < QuestionWords.size(); i++) {
                double sim = 0;
                try {
                    sim = cosineSimilarity(QuestionWords.get(i), this.Value);
                } catch (Exception Ex) {
                    System.out.println("calculating sim failed.");
                }
                temp.addValue(sim);
            }
            Relevance = temp.getMean();
        }
    }

    //================================================================================================
    public static ArrayList<String> extractQuestionWords() {
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
                    temp.add(Integer.toString(num));
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
                temp += d1 + d2;
            }
            stat.addValue(temp);
        }
        Double out = (double) stat.getSum();
        return out.isNaN() ? 0.0 : out;
    }

    public ArrayList<String> extractQueryColumns(String query) throws SQLException {
        ResultSet result = getResultSet(query);
        System.out.println("Result set ready.");
        ArrayList<String> ResultsColumns = new ArrayList<>();
        ResultSetMetaData rsmd = result.getMetaData();
        for (int i = 2; i <= rsmd.getColumnCount(); i++) {
            String name = rsmd.getColumnName(i);
            //System.out.println(name);
            ResultsColumns.add(name);
        }
        return ResultsColumns;
    }

    public synchronized ResultSet getResultSet(String query) throws SQLException {
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
                WORDSTAGS.put(word, pos);
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
        String url = "jdbc:mysql://localhost:3306/" + Dataset + "?useSSL=false";
        String username = "root";
        String password = "farhad";
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
        try {
            int num1 = Integer.parseInt(word1);
            int num2 = Integer.parseInt(word2);
            if (num1 >= 1700 && num1 <= 2020 && num2 >= 1700 && num2 <= 2020) {
                return Math.abs(num1 - num2);
            }
        } catch (Exception Ex) {
            if (word1.toLowerCase().equals(word2.toLowerCase())) {
                return 1;
            }
        }

        if (WORDSTAGS.contains(word1)) {
            if (WORDSTAGS.get(word1).equals("NNP")) {
                if (word1.toLowerCase().equals(word2.toLowerCase())) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
        if (WORDSTAGS.contains(word2)) {
            if (WORDSTAGS.get(word2).equals("NNP")) {
                if (word1.toLowerCase().equals(word2.toLowerCase())) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }

        StringKey key = new StringKey(word1, word2);
        if (SimiliartiesHashMap.containsKey(key)) {
            Double temp = SimiliartiesHashMap.get(key);
            return temp;
        } else {
            Float[] wordAvec = null;
            Float[] wordBvec = null;
            try {
                wordAvec = getVector(word1.toLowerCase());
                wordBvec = getVector(word2.toLowerCase());
            } catch (Exception Ex) {
                System.out.println("Failed getting vector: " + Ex.getMessage());
                return -1;
            }
            if (wordAvec == null || wordBvec == null) {
                //System.out.println("The word " + word1 + " or " + word2 + " is not in conceptnet.");
                return -1;
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
            SimiliartiesHashMap.put(key, temp);
            return temp;
        }
    }

    public double eulideanSimilarity(String word1, String word2) {
        Float[] wordAvec = null;
        Float[] wordBvec = null;
        try {
            wordAvec = getVector(word1.toLowerCase());
            wordBvec = getVector(word2.toLowerCase());
        } catch (Exception Ex) {
            System.out.println("Failed getting vector: " + Ex.getMessage());
            return -1;
        }
        if (wordAvec == null || wordBvec == null) {
            System.out.println("The word " + word1 + " or " + word2 + " is not in conceptnet.");
            return -1;
        }

        double diff_square_sum = 0.0;
        for (int i = 0; i < wordAvec.length; i++) {
            diff_square_sum += (wordAvec[i] - wordBvec[i]) * (wordAvec[i] - wordBvec[i]);
        }
        return Math.sqrt(diff_square_sum);
    }
    //================================================================================================
}
