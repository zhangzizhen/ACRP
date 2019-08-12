import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Scanner;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.DoubleParam;

class Crew {
	String crew_no;
	int crew_rank;
	double crew_cost;
	ArrayList<String> fleet;
}

class Pairing {
	String pairing_no;
	String pairing_str;
	ArrayList<String> fleet;
	Date str_dt;
	Date end_dt;
	double fly_time;
	double duty_time;
	ArrayList<Integer> cc;
	int tcc;
}

public class Main {

	static ArrayList<Pairing> ap;
	static ArrayList<Crew> ac;
	static int slot = 0;
	static boolean match[][];
	
	static void readCrew(String filename) {
		ac = new ArrayList<Crew> ();
		try {
			Scanner scanner = new Scanner(new File(filename));
			while (scanner.hasNext()) {
				Crew c = new Crew ();
				c.crew_no = scanner.next();
				c.crew_rank = scanner.nextInt();
				c.crew_cost = scanner.nextDouble();
				c.fleet = new ArrayList<String> ();
				while (true) {
					String s = scanner.next();
					String ss[] = s.split("[\\s+\\[,\\]]");
					for (String is: ss)
						if (!is.equals("")) c.fleet.add(is);
					if (s.charAt(s.length() - 1) == ']') break;
				}
				ac.add(c);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	static void readPairing(String filename) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		ap = new ArrayList<Pairing> ();
		try {
			Scanner scanner = new Scanner(new File(filename));
			while (scanner.hasNext()) {
				Pairing p = new Pairing ();
				p.pairing_no = scanner.next();
				p.fleet = new ArrayList<String> ();
				while (true) {
					String s = scanner.next();
					String ss[] = s.split("[\\s+\\[,\\]]");
					for (String is: ss)
						if (!is.equals("")) p.fleet.add(is);
					if (s.charAt(s.length() - 1) == ']') break;
				}
				p.str_dt = sdf.parse(scanner.next() + " " + scanner.next());
				p.end_dt = sdf.parse(scanner.next() + " " + scanner.next());
				p.fly_time = scanner.nextDouble();
				p.fly_time /= 3600;
				p.duty_time = scanner.nextDouble();
				p.duty_time /= 3600;
				p.cc = new ArrayList<Integer> ();
				p.tcc = 0;
				for (int i = 0; i < 5; i++) {
					int k = scanner.nextInt();
					p.cc.add(k);
					p.tcc += k;
				}
				slot += p.tcc;
				ap.add(p);
			}
			scanner.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	static boolean validate(Crew c, Pairing p) {
		int i, j;
		int i1 = c.fleet.size(), j1 = p.fleet.size();
		for (j = 0, i = 0; j < j1; j++) {
			for (;i < i1; i++)
				if (c.fleet.get(i).equals(p.fleet.get(j))) break;
			if (i == i1) return false;
		}
		return true;
	}
	
	static void findMatch() {
		match = new boolean [ac.size()][ap.size()];
		int i, j;
		for (Crew c: ac)
			Collections.sort(c.fleet);
		for (Pairing p: ap)
			Collections.sort(p.fleet);
		
		for (i = 0; i < ac.size(); i++)
		for (j = 0; j < ap.size(); j++)
			match[i][j] = validate(ac.get(i), ap.get(j));
	}
	
	static boolean overlap(Pairing p1, Pairing p2) {
		long str1 = p1.str_dt.getTime();
		long end1 = p1.end_dt.getTime();
		long str2 = p2.str_dt.getTime();
		long end2 = p2.end_dt.getTime();
		if (str1 > end2 + 7200000 || str2 > end1 + 7200000) return false;
		else return true;
	}
	
	static void model() {
		try {
			int i, j, jj, k;
			
			IloCplex cplex = new IloCplex();
			IloLinearNumExpr expr = cplex.linearNumExpr();
			
			IloIntVar x[][][] = new IloIntVar [ac.size()][ap.size()][5];
			IloIntVar y[] = new IloIntVar [ap.size()];

			for (i = 0; i < ac.size(); i++)
			for (j = 0; j < ap.size(); j++)
			for (k = 0; k < 5; k++)
				x[i][j][k] = cplex.boolVar();

			for (j = 0; j < ap.size(); j++)
				y[j] = cplex.boolVar();
			
			expr.clear();
			for (j = 0; j < ap.size(); j++)
				expr.addTerm(10000, y[j]);

			for (i = 0; i < ac.size(); i++)
			for (j = 0; j < ap.size(); j++)
			for (k = 0; k < 5; k++)
				expr.addTerm(1, x[i][j][k]);
			cplex.addMaximize(expr);

			for (j = 0; j < ap.size(); j++) {
				expr.clear();
				for (i = 0; i < ac.size(); i++)
				for (k = 0; k < 5; k++)
					expr.addTerm(1, x[i][j][k]);
				expr.addTerm(-ap.get(j).tcc, y[j]);
				cplex.addGe(expr, 0);
			}
			
			for (i = 0; i < ac.size(); i++)
			for (j = 0; j < ap.size(); j++)
			if (match[i][j] == false) {
				for (k = 0; k < 5; k++)
					cplex.addEq(x[i][j][k], 0);
			} else {
				int rank = ac.get(i).crew_rank;
				expr.clear();
				for (k = 0; k < 5; k++) {
					if (!(rank == k || rank == k - 1)) cplex.addEq(x[i][j][k], 0);	// Allow downrank
					expr.addTerm(1, x[i][j][k]);
				}
				cplex.addLe(expr, 1);
			}
			
			for (j = 0; j < ap.size(); j++)
			for (k = 0; k < 5; k++) {
				int num = ap.get(j).cc.get(k);
				expr.clear();
				for (i = 0; i < ac.size(); i++)
					expr.addTerm(1, x[i][j][k]);
				cplex.addLe(expr, num);
			}
			
			for (j = 0; j < ap.size(); j++)
			for (jj = j + 1; jj < ap.size(); jj++)
			if (overlap(ap.get(j), ap.get(jj))) {
				for (i = 0; i < ac.size(); i++) {
					expr.clear();
					for (k = 0; k < 5; k++) {
						expr.addTerm(1, x[i][j][k]);
						expr.addTerm(1, x[i][jj][k]);
					}
					cplex.addLe(expr, 1);
				}
			}
//			cplex.setParam(DoubleParam.TiLim, 20);
		
			if (cplex.solve()) {
				System.out.println(cplex.getStatus());
				System.out.println(cplex.getObjValue());
				
				int c1 = (int) (cplex.getObjValue() / 10000);
				int c2 = (int) (cplex.getObjValue() - c1 * 10000);
				System.out.println(c1 + "/" + ap.size());
				System.out.println(c2 + "/" + slot);
				int rx[][][] = new int [ac.size()][ap.size()][5];
				for (i = 0; i < ac.size(); i++)
				for (j = 0; j < ap.size(); j++)
				for (k = 0; k < 5; k++)
				if (cplex.getValue(x[i][j][k]) > 0.9) rx[i][j][k] = 1;
					else rx[i][j][k] = 0;
				int cov = 0;
				for (j = 0; j < ap.size(); j++) {
					int t = 0;
					for (i = 0; i < ac.size(); i++)
					for (k = 0; k < 5; k++)
						t += rx[i][j][k];
					if (t == ap.get(j).tcc) cov++;
				}
				System.out.println(cov + "/" + ap.size());
			}
			cplex.end();
		
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) {
		readCrew("crew.in");
		readPairing("I-20-A.in");
		findMatch();
		model();
	}	
}
