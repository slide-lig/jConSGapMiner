/*
	This file is part of jConSGapMiner - see https://github.com/slide-lig/jConSGapMiner
	
	Copyright 2014 Vincent Leroy, Université Joseph Fourier and CNRS

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	 http://www.apache.org/licenses/LICENSE-2.0
	 
	or see the LICENSE.txt file joined with this program.

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
 */

package fr.liglab.consgap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.liglab.consgap.internals.Dataset;
import fr.liglab.consgap.internals.MiningStep;
import fr.liglab.consgap.internals.MiningThread;
import fr.liglab.consgap.internals.bitset.BitSetDataset;

public class Main {
	public static void main(String[] args) throws IOException {
		long startTime = System.currentTimeMillis();
		Dataset ds = new BitSetDataset(args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]),
				Integer.parseInt(args[4]));
		MiningStep initState = new MiningStep(ds);
		final int nbThreads = Runtime.getRuntime().availableProcessors();
		List<MiningThread> threads = new ArrayList<MiningThread>(nbThreads);
		for (int id = 0; id < nbThreads; id++) {
			threads.add(new MiningThread(id, threads));
		}
		for (MiningThread t : threads) {
			t.init(initState);
			t.start();
		}
		for (MiningThread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		long removeRedundantStart = System.currentTimeMillis();
		List<int[]> minimalEmerging = ds.getResultsCollector().getNonRedundant();
		for (int[] seq : minimalEmerging) {
			// System.out.println(Arrays.toString(seq));
		}
		long endTime = System.currentTimeMillis();
		System.out.println("total minimal emerging sequences = " + minimalEmerging.size()
				+ "\ntotal sequences collected = " + ds.getResultsCollector().getNbEmergingSeqCollected());
		System.out.println("execution time " + (endTime - startTime) + " ms including "
				+ (endTime - removeRedundantStart) + " ms removing redundant results " + MiningStep.loopCounts.get());
	}
}
