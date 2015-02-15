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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import fr.liglab.consgap.collector.BatchFilteringResultsCollector;
import fr.liglab.consgap.collector.OrderedResultsCollector;
import fr.liglab.consgap.collector.PostFilteringPrefixCollector;
import fr.liglab.consgap.collector.PostFilteringResultsCollector;
import fr.liglab.consgap.collector.PrefixCollector;
import fr.liglab.consgap.collector.ResultsCollector;
import fr.liglab.consgap.dataset.Dataset;
import fr.liglab.consgap.dataset.lcmstyle.TransactionsBasedDataset;
import fr.liglab.consgap.executor.BreadthFirstExecutor;
import fr.liglab.consgap.executor.DepthFirstExecutor;
import fr.liglab.consgap.executor.MiningExecutor;

public class Main {
	public static void main(String[] args) throws IOException {
		Options options = new Options();
		CommandLineParser parser = new PosixParser();

		options.addOption("b", false, "Benchmark mode : sequences are not outputted at all");
		options.addOption("h", false, "Show help");
		options.addOption("w", false, "Use breadth first exploration instead of depth first. Usually less efficient");
		options.addOption("t", true, "How many threads will be launched (defaults to your machine's processors count)");
		options.addOption("p", false, "Prune backspace");
		options.addOption("e", false, "Prune contains emerging");
		options.addOption("s", false, "Prune sibling emerging");
		options.addOption(
				"f",
				true,
				"Sequences filtering frequency, expressed in number of outputs. Recommended value is 100, avoids some redundant explorations.");
		options.addOption("sep", true, "separator in the dataset files (defaults to tabulation)");
		try {
			CommandLine cmd = parser.parse(options, args);

			if (cmd.getArgs().length != 5 || cmd.hasOption('h')) {
				printMan(options);
			} else {
				standalone(cmd);
			}
		} catch (ParseException e) {
			printMan(options);
		}
	}

	private static void printMan(Options options) {
		String syntax = "java fr.liglab.consgap.Main [OPTIONS] INPUT_POS_DATASET INPUT_NEG_DATASET MINSUP_IN_POS MAXSUP_IN_NEG MAX_GAP";
		String header = "\nOptions are :";
		String footer = "Copyright 2014 Vincent Leroy, Université Joseph Fourier and CNRS";

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(80, syntax, header, options, footer);
	}

	private static void standalone(CommandLine cmd) throws IOException {
		if (cmd.hasOption("sep")) {
			ConfStats.separator = cmd.getOptionValue("sep");
		}
		if (cmd.hasOption('t')) {
			ConfStats.nbThreads = Math.max(1, Integer.parseInt(cmd.getOptionValue('t')));
		} else {
			ConfStats.nbThreads = Runtime.getRuntime().availableProcessors();
		}
		if (cmd.hasOption('w')) {
			ConfStats.breadthFirst = true;
		} else {
			ConfStats.breadthFirst = false;
		}
		if (cmd.hasOption('f')) {
			ConfStats.minimizeDuringExec = true;
			ConfStats.minimizeThreshold = Integer.parseInt(cmd.getOptionValue('f'));
		} else {
			ConfStats.minimizeDuringExec = false;
		}
		if (cmd.hasOption('p')) {
			ConfStats.pruneBackspace = true;
		} else {
			ConfStats.pruneBackspace = false;
		}
		if (cmd.hasOption('b')) {
			ConfStats.benchmark = true;
		} else {
			ConfStats.benchmark = false;
		}
		if (cmd.hasOption('e')) {
			ConfStats.pruneContainsEmerging = true;
		} else {
			ConfStats.pruneContainsEmerging = false;
		}
		if (cmd.hasOption('s')) {
			ConfStats.pruneSiblingsEmerging = true;
		} else {
			ConfStats.pruneSiblingsEmerging = false;
		}
		ConfStats.checkConfCoherent();
		ResultsCollector collector;
		MiningExecutor executor;
		if (ConfStats.breadthFirst) {
			collector = new OrderedResultsCollector();
			executor = new BreadthFirstExecutor(ConfStats.nbThreads);
		} else {
			executor = new DepthFirstExecutor(ConfStats.nbThreads);
			if (ConfStats.minimizeDuringExec) {
				collector = new BatchFilteringResultsCollector(ConfStats.minimizeThreshold);
			} else {
				collector = new PostFilteringResultsCollector();
			}
		}
		PrefixCollector prefixCollector = null;
		if (ConfStats.pruneBackspace) {
			prefixCollector = new PostFilteringPrefixCollector();
		}
		Dataset dataset;
		dataset = new TransactionsBasedDataset(collector, prefixCollector, cmd.getArgs()[0], cmd.getArgs()[1],
				Integer.parseInt(cmd.getArgs()[2]), Integer.parseInt(cmd.getArgs()[3]),
				Integer.parseInt(cmd.getArgs()[4]));
		// useful to give yourkit time to attach
		// try {
		// Thread.sleep(5000);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		ConfStats.startStatsThread();
		long startTime = System.currentTimeMillis();
		executor.mine(dataset);
		long removeRedundantStart = System.currentTimeMillis();
		List<String[]> minimalEmerging = dataset.getResultsCollector().getNonRedundant();

		if (!ConfStats.benchmark) {
			for (String[] seq : minimalEmerging) {
				for (int i = 0; i < seq.length; i++) {
					System.out.print(seq[i] + "\t");
				}
				System.out.println();
			}
		}
		long endTime = System.currentTimeMillis();
		System.out.println("total minimal emerging sequences = " + minimalEmerging.size()
				+ "\ntotal sequences collected = " + dataset.getResultsCollector().getNbCollected());
		System.out.println("execution time " + (endTime - startTime) + " ms including "
				+ (endTime - removeRedundantStart) + " ms removing redundant results");
		ConfStats.printShortStats();
	}
}
