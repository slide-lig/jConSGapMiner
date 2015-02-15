package fr.liglab.consgap.validation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CompareResults {
	public static void main(String[] args) throws Exception {
		String compFile = "/Users/vleroy/Workspace/emerging/emergseqs_gap2.txt";
		String refFile = "/Users/vleroy/Workspace/emerging/emergeseq_gap2-new.txt";
		boolean strict = true;
		List<List<Integer>> compRes = new ArrayList<>();
		BufferedReader br = new BufferedReader(new FileReader(compFile));
		String line;
		while ((line = br.readLine()) != null) {
			String[] sp = line.split("\\s");
			List<Integer> pattern = new ArrayList<>();
			for (String s : sp) {
				if (!s.startsWith("(")) {
					pattern.add(Integer.valueOf(s));
				}
			}
			compRes.add(pattern);
		}
		br.close();
		br = new BufferedReader(new FileReader(refFile));
		while ((line = br.readLine()) != null) {
			String[] sp = line.split("\\s");
			List<Integer> pattern = new ArrayList<>();
			for (String s : sp) {
				if (!s.startsWith("(")) {
					pattern.add(Integer.valueOf(s));
				}
			}
			boolean found = false;
			for (List<Integer> comp : compRes) {
				if (strict) {
					if (comp.size() == pattern.size()) {
						found = true;
						for (int i = 0; i < comp.size(); i++) {
							if (!comp.get(i).equals(pattern.get(i))) {
								found = false;
								break;
							}
						}
					}
				} else {
					if (comp.size() >= pattern.size()) {
						int index = 0;
						for (int i = 0; i < comp.size(); i++) {
							if (comp.get(i).equals(pattern.get(index))) {
								index++;
							}
							if (index == pattern.size()) {
								found = true;
								break;
							}
						}
					}
				}
				if (found) {
					break;
				}
			}
			if (!found) {
				System.err.println(pattern);
			}
		}
		br.close();
	}
}
