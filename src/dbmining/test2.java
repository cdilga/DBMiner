/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dbmining;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 *
 * @author fzafari
 */
public class test2 {

    public static void main(String args[]) {
        /*
        System.out.println("|T|F|T|T|");
        System.out.println("|T|F|T|T|");
        System.out.println("|F|F|F|F|");
        System.out.println("|T|F|T|T|");
        Random r = new Random();
        DecimalFormat f = new DecimalFormat("##.00");
        System.out.println("|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|");
        System.out.println("|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|");
        System.out.println("|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|");
        System.out.println("|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|" + f.format(r.nextDouble()) + "|");
         */
        Table<Integer, Integer, Object> table1 = HashBasedTable.create();
        Random r = new Random();
        DecimalFormat f = new DecimalFormat("0.00");
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                table1.put(i, j, Double.parseDouble(f.format(r.nextDouble()).toString()));
            }
        }
        /*
        //-------------------------------------
        table1.put(0, 0, 0.29);
        table1.put(0, 1, 0.27);
        table1.put(0, 2, 0.85);
        table1.put(0, 3, 0.42);
        table1.put(0, 4, 0.94);
        //-------------------------------------
        table1.put(1, 0, 0.92);
        table1.put(1, 1, 0.43);
        table1.put(1, 2, 0.51);
        table1.put(1, 3, 0.33);
        table1.put(1, 4, 0.94);
        //-------------------------------------
        table1.put(2, 0, 0.42);
        table1.put(2, 1, 0.90);
        table1.put(2, 2, 0.73);
        table1.put(2, 3, 0.46);
        table1.put(2, 4, 0.94);
        //-------------------------------------
        table1.put(3, 0, 0.30);
        table1.put(3, 1, 0.67);
        table1.put(3, 2, 0.71);
        table1.put(3, 3, 0.24);
        table1.put(3, 4, 0.94);
        //-------------------------------------
        */
        print(table1);

        int numRows = table1.rowKeySet().size();
        int numCols = table1.columnKeySet().size();

        double sum = 0;
        int n = 0;
        Iterator i = table1.rowKeySet().iterator();
        while (i.hasNext()) {
            int row = (int) i.next();
            Iterator j = table1.columnKeySet().iterator();
            while (j.hasNext()) {
                int column = (int) j.next();
                Double value = (Double) table1.get(row, column);
                sum += value;
                n++;
            }
        }
        double avg = (double) sum / n;

        Table<Integer, Integer, Object> table2 = HashBasedTable.create();
        i = table1.rowKeySet().iterator();
        while (i.hasNext()) {
            int row = (int) i.next();
            Iterator j = table1.columnKeySet().iterator();
            while (j.hasNext()) {
                int column = (int) j.next();
                Double value = (Double) table1.get(row, column);
                if (value > avg) {
                    table2.put(row, column, "T");
                } else {
                    table2.put(row, column, "F");
                }
            }
        }
        print(table2);

        //========================================================================================
        ArrayList<Map<Integer, Object>> rows = new ArrayList<>();
        HashMap<Integer, Object> row0 = new HashMap<Integer, Object>();
        Map<Integer, Object> row1 = table2.row(0);
        for (int col = 0; col < numCols; col++) {
            row0.put(col, "F");
        }

        System.out.println(row0);
        System.out.println(row1);

        rows.add(row1);

        i = table1.rowKeySet().iterator();
        while (i.hasNext()) {
            int row = (int) i.next();
            Map<Integer, Object> rown = table2.row(row);
            if (numChanges(row1, rown) < numChangesF(rown)) {
                //In this case, we will change it to row1;
                rows.add(row1);
            } else {
                rows.add(row0);
            }
        }

        Table<Integer, Integer, Object> table3 = HashBasedTable.create();
        for (int u = 0; u < numRows; u++) {
            for (int v = 0; v < numCols; v++) {
                table3.put(u, v, rows.get(u).get(v));
            }
        }
        print(table3);
        //========================================================================================
    }

    public static int numChanges(Map<Integer, Object> row1, Map<Integer, Object> row2) {
        int temp = 0;
        Iterator i = row1.keySet().iterator();
        while (i.hasNext()) {
            int column = (int) i.next();
            if (!row1.get(column).equals(row2.get(column))) {
                temp++;
            }
        }
        return temp;
    }

    public static int numChangesF(Map<Integer, Object> row1) {
        int temp = 0;
        Iterator i = row1.keySet().iterator();
        while (i.hasNext()) {
            int column = (int) i.next();
            if (!row1.get(column).equals("F")) {
                temp++;
            }
        }
        return temp;
    }

    public static void print(Table<Integer, Integer, Object> table) {
        String temp = "";
        Iterator i = table.rowKeySet().iterator();
        while (i.hasNext()) {
            int row = (int) i.next();
            temp += "|";
            Iterator j = table.columnKeySet().iterator();
            while (j.hasNext()) {
                int column = (int) j.next();
                String value = table.get(row, column).toString();
                temp += value + "|";
            }
            temp += "\n";
        }
        System.out.println(temp);
    }
}
