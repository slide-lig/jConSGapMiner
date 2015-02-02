package fr.liglab.consgap.validation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FindSupport {
	public static boolean match(int[] pattern, int[] transaction, int gap) {
		for (int i = 0; i < transaction.length; i++) {
			if (transaction[i] == pattern[0]) {
				if (match(pattern, transaction, gap, 1, i + 1)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean match(int[] pattern, int[] transaction, int gap, int patternIndex, int transactionIndex) {
		if (patternIndex == pattern.length) {
			return true;
		}
		for (int i = transactionIndex; i < transactionIndex + 1 + gap && i < transaction.length; i++) {
			if (transaction[i] == pattern[patternIndex]) {
				if (match(pattern, transaction, gap, patternIndex + 1, i + 1)) {
					return true;
				}
			}
		}
		return false;
	}

	public static List<int[]> matchPos(int[] pattern, int[] transaction, int gap) {
		List<int[]> matchingPositions = new ArrayList<>();
		for (int i = 0; i < transaction.length; i++) {
			if (transaction[i] == pattern[0]) {
				int[] posSoFar = new int[pattern.length];
				posSoFar[0] = i;
				matchPos(pattern, transaction, gap, 1, i + 1, posSoFar, matchingPositions);
			}
		}
		return matchingPositions;
	}

	public static void matchPos(int[] pattern, int[] transaction, int gap, int patternIndex, int transactionIndex,
			int[] posSoFar, List<int[]> res) {
		if (patternIndex == pattern.length) {
			res.add(posSoFar);
			return;
		}
		for (int i = transactionIndex; i < transactionIndex + 1 + gap && i < transaction.length; i++) {
			if (transaction[i] == pattern[patternIndex]) {
				int[] newPos = Arrays.copyOf(posSoFar, posSoFar.length);
				newPos[patternIndex] = i;
				matchPos(pattern, transaction, gap, patternIndex + 1, i + 1, newPos, res);
			}
		}
	}

	public static List<Set<Integer>> getBackSpace(List<List<int[]>> positions, List<int[]> dataset, int gap) {
		List<Set<Integer>> res = new ArrayList<>();
		for (int i = 0; i < positions.size(); i++) {
			if (positions.get(i) != null) {
				for (int[] pos : positions.get(i)) {
					if (res.isEmpty()) {
						for (int j = 0; j < pos.length; j++) {
							int startPos;
							int endPos;
							if (j == 0) {
								startPos = Math.max(0, pos[0] - gap);
								endPos = pos[0];
							} else {
								startPos = pos[j - 1] + 1;
								endPos = pos[j];
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
							if (j == 0) {
								startPos = Math.max(0, pos[0] - gap);
								endPos = pos[0];
							} else {
								startPos = pos[j - 1] + 1;
								endPos = pos[j];
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

	public static void main(String[] args) throws Exception {
		int[] seq = { 7972, 8337 };
		int gap = 1;
		String datasetFile = "/Users/vleroy/Workspace/emerging/Dall_as_nums.txt_tiny";
		while (true) {
			BufferedReader br = new BufferedReader(new FileReader(datasetFile));
			String line;
			int support = 0;
			List<int[]> dataset = new ArrayList<>();
			List<List<int[]>> matchingPos = new ArrayList<>();
			while ((line = br.readLine()) != null) {
				String[] sp = line.split("\\s");
				int[] transaction = new int[sp.length];
				for (int i = 0; i < sp.length; i++) {
					transaction[i] = Integer.parseInt(sp[i]);
				}
				if (match(seq, transaction, gap)) {
					support++;
					matchingPos.add(matchPos(seq, transaction, gap));
				} else {
					matchingPos.add(null);
				}
				dataset.add(transaction);
			}
			br.close();
			System.out.println(Arrays.toString(seq) + " support=" + support);
			List<Set<Integer>> backSpaces = getBackSpace(matchingPos, dataset, gap);
			System.out.println(backSpaces);
			int additions = 0;
			for (Set<Integer> s : backSpaces) {
				if (!s.isEmpty()) {
					additions++;
				}
			}
			if (additions == 0) {
				break;
			} else {
				int[] newSeq = new int[seq.length + additions];
				int writeDelta = 0;
				for (int i = 0; i < seq.length; i++) {
					if (!backSpaces.get(i).isEmpty()) {
						newSeq[i + writeDelta] = backSpaces.get(i).iterator().next();
						writeDelta++;
					}
					newSeq[i + writeDelta] = seq[i];
				}
				seq = newSeq;
			}
		}

	}
}
