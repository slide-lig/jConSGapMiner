package fr.liglab.consgap.validation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BackSpaceChecker {
	List<int[]> dataset;
	int gap;

	public BackSpaceChecker(String datasetFile, int gap) {
		this.gap = gap;
		this.dataset = new ArrayList<>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(datasetFile));
			String line;
			while ((line = br.readLine()) != null) {
				String[] sp = line.split("\\s+");
				int[] trans = new int[sp.length];
				for (int i = 0; i < sp.length; i++) {
					trans[i] = Integer.parseInt(sp[i]);
				}
				dataset.add(trans);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public BackSpaceChecker() {
		this("/Users/vleroy/Workspace/emerging/Dall_as_nums.txt_tiny", 2);
	}

	public Set<Integer> getBackSpace(String[] seq) {
		int[] intSeq = new int[seq.length];
		for (int i = 0; i < seq.length; i++) {
			intSeq[i] = Integer.parseInt(seq[i]);
		}
		return this.getBackSpace(intSeq);
	}

	public Set<Integer> getBackSpace(int[] seq) {
		List<List<int[]>> matchingPos = new ArrayList<>();
		for (int[] transaction : this.dataset) {
			if (match(seq, transaction, gap)) {
				matchingPos.add(matchPos(seq, transaction, gap));
			} else {
				matchingPos.add(null);
			}
		}
		List<Set<Integer>> backSpaces = getBackSpace(matchingPos, dataset, gap);
		return backSpaces.get(backSpaces.size() - 1);
	}

	public List<List<int[]>> getOccurencesPositions(int[] seq) {
		List<List<int[]>> matchingPos = new ArrayList<>();
		for (int[] transaction : this.dataset) {
			if (match(seq, transaction, gap)) {
				matchingPos.add(matchPos(seq, transaction, gap));
			} else {
				matchingPos.add(null);
			}
		}
		return matchingPos;
	}

	private static boolean match(int[] pattern, int[] transaction, int gap) {
		for (int i = pattern.length - 1; i < transaction.length; i++) {
			if (transaction[i] == pattern[pattern.length - 1]) {
				if (match(pattern, transaction, gap, pattern.length - 2, i - 1)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean match(int[] pattern, int[] transaction, int gap, int patternIndex, int transactionIndex) {
		if (patternIndex == -1) {
			return true;
		}
		for (int i = transactionIndex; i >= transactionIndex - gap && i >= 0; i--) {
			if (transaction[i] == pattern[patternIndex]) {
				if (match(pattern, transaction, gap, patternIndex - 1, i - 1)) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<int[]> matchPos(int[] pattern, int[] transaction, int gap) {
		List<int[]> matchingPositions = new ArrayList<>();
		for (int i = transaction.length - 1; i >= 0; i--) {
			if (transaction[i] == pattern[pattern.length - 1]) {
				int[] posSoFar = new int[pattern.length];
				posSoFar[pattern.length - 1] = i;
				matchPos(pattern, transaction, gap, pattern.length - 2, i - 1, posSoFar, matchingPositions);
			}
		}
		return matchingPositions;
	}

	public static void matchPos(int[] pattern, int[] transaction, int gap, int patternIndex, int transactionIndex,
			int[] posSoFar, List<int[]> res) {
		if (patternIndex == -1) {
			res.add(posSoFar);
			return;
		}
		for (int i = transactionIndex; i >= transactionIndex - gap && i >= 0; i--) {
			if (transaction[i] == pattern[patternIndex]) {
				int[] newPos = Arrays.copyOf(posSoFar, posSoFar.length);
				newPos[patternIndex] = i;
				matchPos(pattern, transaction, gap, patternIndex - 1, i - 1, newPos, res);
			}
		}
	}

	private static List<Set<Integer>> getBackSpace(List<List<int[]>> positions, List<int[]> dataset, int gap) {
		List<Set<Integer>> res = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++) {
			if (positions.get(i) != null) {
				for (int[] pos : positions.get(i)) {
					if (res.isEmpty()) {
						for (int j = 0; j < pos.length; j++) {
							int startPos;
							int endPos;
							if (j == pos.length - 1) {
								startPos = pos[pos.length - 1] + 1;
								endPos = Math.min(dataset.get(i).length, pos[pos.length - 1] + gap + 2);
							} else {
								startPos = pos[j] + 1;
								endPos = pos[j + 1];
							}
							Set<Integer> s = new HashSet<>();
							for (int k = startPos; k < endPos; k++) {
								s.add(dataset.get(i)[k]);
							}
							res.add(s);
						}
					} else {
						for (int j = 0; j < pos.length; j++) {
							Set<Integer> inter = new HashSet<>();
							int startPos;
							int endPos;
							if (j == pos.length - 1) {
								startPos = pos[pos.length - 1] + 1;
								endPos = Math.min(dataset.get(i).length, pos[pos.length - 1] + gap + 2);
							} else {
								startPos = pos[j] + 1;
								endPos = pos[j + 1];
							}
							for (int k = startPos; k < endPos; k++) {
								inter.add(dataset.get(i)[k]);
							}
							res.get(j).retainAll(inter);
						}
					}
				}
			}
		}
		return res;
	}

	public static void main(String[] args) {
		BackSpaceChecker bs = new BackSpaceChecker();
		List<List<int[]>> pos = bs.getOccurencesPositions(new int[] { 8394, 7121, 7972 });
		int lineNum = 0;
		for (List<int[]> p : pos) {
			lineNum++;
			if (p == null) {
				System.out.println("occurences at line " + lineNum + ": 0");
			} else {
				System.out.println("occurences at line " + lineNum + ": " + p.size());
				for (int[] oc : p) {
					System.out.println(Arrays.toString(oc));
				}
			}
		}
	}

}
