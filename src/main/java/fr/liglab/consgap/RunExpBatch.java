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
import java.util.List;

import fr.liglab.consgap.collector.PostFilteringPrefixCollector;
import fr.liglab.consgap.collector.PostFilteringResultsCollector;
import fr.liglab.consgap.collector.PrefixCollector;
import fr.liglab.consgap.collector.ResultsCollector;
import fr.liglab.consgap.dataset.Dataset;
import fr.liglab.consgap.dataset.lcmstyle.TransactionsBasedDataset;
import fr.liglab.consgap.executor.DepthFirstExecutor;
import fr.liglab.consgap.executor.MiningExecutor;

public class RunExpBatch {

	public static void main(String[] args) throws IOException {
		ConfStats.separator = "\\s";
		int nbThreads = 10;
		String posFile = args[0];
		String negFile = args[2];
		int posFileNbLines = Integer.parseInt(args[1]);
		int negFileNbLines = Integer.parseInt(args[3]);
		int gap = Integer.parseInt(args[4]);
		for (double posBoundD : new double[] { 0.95, 0.9, 0.85, 0.8, 0.75, 0.7 }) {
			for (double negBoundD : new double[] { 0.3, 0.25, 0.2, 0.15, 0.1, 0.05 }) {
				System.out.println("gap=" + gap + " posBound=" + (int) (posBoundD * posFileNbLines) + " negBound="
						+ (int) (negBoundD * negFileNbLines));
				standalone(nbThreads, (int) (posBoundD * posFileNbLines), (int) (negBoundD * negFileNbLines), posFile,
						negFile, gap);
			}
		}
	}

	private static void standalone(int nbThreads, int posBound, int negBound, String posFile, String negFile, int gap)
			throws IOException {
		ResultsCollector collector;
		MiningExecutor executor;
		executor = new DepthFirstExecutor(nbThreads);
		collector = new PostFilteringResultsCollector();
		PrefixCollector prefixCollector = null;
		prefixCollector = new PostFilteringPrefixCollector();
		Dataset dataset;
		dataset = new TransactionsBasedDataset(collector, prefixCollector, posFile, negFile, posBound, negBound, gap);
		long startTime = System.currentTimeMillis();
		executor.mine(dataset);
		long removeRedundantStart = System.currentTimeMillis();
		List<String[]> minimalEmerging = dataset.getResultsCollector().getNonRedundant();
		long endTime = System.currentTimeMillis();
		System.out.println("total minimal emerging sequences = " + minimalEmerging.size()
				+ "\ntotal sequences collected = " + dataset.getResultsCollector().getNbCollected());
		System.out.println("execution time " + (endTime - startTime) + " ms including "
				+ (endTime - removeRedundantStart) + " ms removing redundant results");
		ConfStats.printShortStats();
	}
}
