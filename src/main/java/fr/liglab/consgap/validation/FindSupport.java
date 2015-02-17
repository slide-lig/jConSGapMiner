package fr.liglab.consgap.validation;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FindSupport {
	public static boolean match(int[] pattern, int[] transaction, int gap) {
		for (int i = pattern.length - 1; i < transaction.length; i++) {
			if (transaction[i] == pattern[pattern.length - 1]) {
				if (match(pattern, transaction, gap, pattern.length - 2, i - 1)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean match(int[] pattern, int[] transaction, int gap, int patternIndex, int transactionIndex) {
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

	public static List<int[]> matchPos(int[] pattern, int[] transaction, int gap) {
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

	public static List<Set<Integer>> getBackSpace(List<List<int[]>> positions, List<int[]> dataset, int gap) {
		// TODO fix from right to left
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

	public static void main(String[] args) throws Exception {
		int[] seq = { 8394, 7121, 7972 };
		int gap = 2;
		String datasetFile = "/Users/vleroy/Workspace/emerging/Dall_as_nums.txt_tiny";
		while (true) {
			BufferedReader br = new BufferedReader(new FileReader(datasetFile));
			String line;
			int lineId = 1;
			int support = 0;
			List<int[]> dataset = new ArrayList<>();
			List<List<int[]>> matchingPos = new ArrayList<>();
			while ((line = br.readLine()) != null) {
				String[] sp = line.split("\\s");
				TIntList transactionl = new TIntArrayList(sp.length);
				for (int i = 0; i < sp.length; i++) {
					transactionl.add(Integer.parseInt(sp[i]));
				}
				int[] transaction = transactionl.toArray();
				if (match(seq, transaction, gap)) {
					support++;
					matchingPos.add(matchPos(seq, transaction, gap));
					System.out.println("line " + lineId + " " + matchingPos.get(matchingPos.size() - 1).size()
							+ " occurences");
					// System.out.println("batch of occurences");
					for (int[] pos : matchingPos.get(matchingPos.size() - 1)) {
						System.out.println("pos: " + Arrays.toString(pos));
						for (int i = pos[0]; i <= pos[pos.length - 1] + gap + 1; i++) {
							System.out.print(transaction[i] + " ");
						}
						System.out.println();
					}
				} else {
					matchingPos.add(null);
				}
				dataset.add(transaction);
				lineId++;
			}
			br.close();
			System.out.println(Arrays.toString(seq) + " support=" + support);
			List<Set<Integer>> backSpaces = getBackSpace(matchingPos, dataset, gap);
			int additions = 0;
			for (Set<Integer> s : backSpaces) {
				if (!s.isEmpty()) {
					additions++;
				}
			}
			System.out.println("additions " + additions);
			if (additions == 0) {
				break;
			} else {
				int[] newSeq = new int[seq.length + additions];
				int writeDelta = 0;
				for (int i = 0; i < seq.length; i++) {
					newSeq[i + writeDelta] = seq[i];
					if (!backSpaces.get(i).isEmpty()) {
						writeDelta++;
						newSeq[i + writeDelta] = backSpaces.get(i).iterator().next();
					}
				}
				seq = newSeq;
			}
			System.out.println(backSpaces);
		}

	}
}
