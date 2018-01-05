package dbmining;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author fzafari
 */
public class test {

    public static Statement OpenConnectionMYSQL() throws SQLException {
        String url = "jdbc:mysql://localhost:3306/species";
        String username = "root";
        String password = "farhad";
        Connection connection = DriverManager.getConnection(url, username, password);
        Statement stmt = connection.createStatement();
        return (Statement) stmt;
    }

    String text = "In which decade were the largest numbers of discoveries made";
    public static BufferedWriter writerout;

    public static void main(String[] args) throws FileNotFoundException, IOException, SQLException, Exception {
        //writerout = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("output.xml"), "utf-8"));
        //(new test()).SyntacticTree();
        //save(OpenConnectionMYSQL());
        System.out.println(getQueryCoverage("Select A,B from test where E <= 25;", "Select A,B,C from test where E <= 20;"));
    }

    public void SyntacticTree() throws IOException {
        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, sentiment, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);
        // run all Annotators on this text
        pipeline.annotate(document);
        // these are all the sentences in this document
        // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap sentence : sentences) {
            // this is the parse tree of the current sentence
            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
            //System.out.println(tree);
            preorder(tree.firstChild());
            System.out.println(tree.firstChild());
        }
    }

    public void preorder(Tree tree) throws IOException {
        if (tree != null) {
            writerout.write("<" + tree.nodeString() + ">");
            writerout.flush();
            for (int i = 0; i < tree.getChildrenAsList().size(); i++) {
                preorder(tree.getChild(i));
            }
            writerout.write("</" + tree.nodeString() + ">");
            writerout.flush();
        }
    }

    public void CorefAnnotator() {
        Annotation document = new Annotation(text);
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,mention,coref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        pipeline.annotate(document);
        System.out.println("---");
        System.out.println("coref chains");

        for (edu.stanford.nlp.hcoref.data.CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class
        ).values()) {
            System.out.println("\t" + cc);

        }
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class
        )) {
            System.out.println("---");
            System.out.println("mentions");

            for (edu.stanford.nlp.hcoref.data.Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class
            )) {
                System.out.println("\t" + m);
            }
        }
    }

    public void SyntacticTree2() throws IOException {
        PrintWriter xmlOut = new PrintWriter("xmlOutput.xml");
        Properties props = new Properties();
        props.setProperty("annotators",
                "tokenize, ssplit, pos, lemma, ner, parse");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation annotation = new Annotation(text);
        pipeline.annotate(annotation);
        pipeline.xmlPrint(annotation, xmlOut);
        // An Annotation is a Map and you can get and use the
        // various analyses individually. For instance, this
        // gets the parse tree of the 1st sentence in the text.
        List<CoreMap> sentences = annotation.get(
                CoreAnnotations.SentencesAnnotation.class);
        if (sentences != null && sentences.size() > 0) {
            CoreMap sentence = sentences.get(0);
            Tree tree = sentence.get(TreeAnnotation.class).firstChild();
            PrintWriter out = new PrintWriter(System.out);
            out.println("The first sentence parsed is:");
            tree.pennPrint(out);
            //System.out.println(tree.pennString());
        }
    }

    public static void save(Statement stmt) throws FileNotFoundException, IOException, SQLException, Exception {
        String columns = "(";
        columns += "AFD_higher_taxon text,"
                + "Or_der text,"
                + "Genus text,"
                + "Subgenus text,"
                + "Species text,"
                + "Subspecies text,"
                + "Name_type text,"
                + "Rank text,"
                + "Orig_combination text,"
                + "Author text,"
                + "Year int,"
                + "Full_author_name text,"
                + "No_auths int,"
                + "A_in_B text,"
                + "Pub_ID text,"
                + "Citation text,"
                + "Pub_type text,"
                + "Pub_country text,"
                + "Pub_ento text,"
                + "J_title text,"
                + "A_publr_class text,"
                + "A_publr text";
        try {
            String sql = "DROP TABLE " + "species.insectdiscoveries";
            stmt.executeUpdate(sql);
        } catch (Exception Ex) {
        }
        String sql = "CREATE TABLE " + "species.insectdiscoveries";
        sql += " " + columns + ");";
        System.out.println(sql);
        stmt.executeUpdate(sql);
        //**************************************
        BufferedReader readerin = new BufferedReader(new FileReader("C:\\Users\\fzafari\\Desktop\\insect_discoveries.csv"));
        String line = readerin.readLine();
        while ((line = readerin.readLine()) != null) {
            sql = "Insert into " + "species.insectdiscoveries";
            sql += "( "
                    + "AFD_higher_taxon,"
                    + "Or_der,Genus,"
                    + "Subgenus,"
                    + "Species,"
                    + "Subspecies,"
                    + "Name_type,"
                    + "Rank,"
                    + "Orig_combination,"
                    + "Author,Year,"
                    + "Full_author_name,"
                    + "No_auths,"
                    + "A_in_B,"
                    + "Pub_ID,"
                    + "Citation,"
                    + "Pub_type,"
                    + "Pub_country,"
                    + "Pub_ento,"
                    + "J_title,"
                    + "A_publr_class,"
                    + "A_publr"
                    + " )"
                    + "Values ( "
                    + " '" + mysql_real_escape_string(line.split(",")[0].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[1].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[2].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[3].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[4].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[5].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[6].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[7].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[8].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[9].replaceAll("\"", "")) + "', "
                    + " " + mysql_real_escape_string(line.split(",")[10].replaceAll("\"", "")) + ", "
                    + " '" + mysql_real_escape_string(line.split(",")[11].replaceAll("\"", "")) + "', "
                    + " " + mysql_real_escape_string(line.split(",")[12].replaceAll("\"", "")) + ", "
                    + " '" + mysql_real_escape_string(line.split(",")[13].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[14].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[15].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[16].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[17].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[18].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[19].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[20].replaceAll("\"", "")) + "', "
                    + " '" + mysql_real_escape_string(line.split(",")[20].replaceAll("\"", "")) + "' "
                    + " );";
            System.out.println(sql);
            stmt.executeUpdate(sql);
        }
    }

    public static String mysql_real_escape_string(String str)
            throws Exception {
        if (str == null) {
            return null;
        }

        if (str.replaceAll("[a-zA-Z0-9_!@#$%^&*()-=+~.;:,\\Q[\\E\\Q]\\E<>{}\\/? ]", "").length() < 1) {
            return str;
        }

        String clean_string = str;
        clean_string = clean_string.replaceAll("\\\\", "\\\\\\\\");
        clean_string = clean_string.replaceAll("\\n", "\\\\n");
        clean_string = clean_string.replaceAll("\\r", "\\\\r");
        clean_string = clean_string.replaceAll("\\t", "\\\\t");
        clean_string = clean_string.replaceAll("\\00", "\\\\0");
        clean_string = clean_string.replaceAll("'", "\\\\'");
        clean_string = clean_string.replaceAll("\\\"", "\\\\\"");

        if (clean_string.replaceAll("[a-zA-Z0-9_!@#$%^&*()-=+~.;:,\\Q[\\E\\Q]\\E<>{}\\/?\\\\\"' ]", "").length() < 1) {
            return clean_string;
        }
        return "";
    }

    public static Statement OpenConnectionMYSQL(String Dataset) throws SQLException {
        String url = "jdbc:mysql://localhost:3306/" + Dataset;
        String username = "root";
        String password = "farhad";
        Connection connection = DriverManager.getConnection(url, username, password);
        Statement stmt = connection.createStatement();
        return (Statement) stmt;
    }

    public static double getQueryCoverage(String groundTruthQuery, String query) throws SQLException {
        //=========================================================================================
        Table<String, String, String> ALL = HashBasedTable.create();
        Statement stmt = OpenConnectionMYSQL("species");
        ResultSet result = stmt.executeQuery("SELECT * FROM test;");
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
        result = stmt.executeQuery(groundTruthQuery);
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
        result = stmt.executeQuery(query);
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
            Table.Cell<String, String, String> c = (Table.Cell) it.next();
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
        System.out.println(FP + " " + FN + " " + N);
        return 1 - ((double) FP / N) - (2 * (double) FN / N);
    }
}
