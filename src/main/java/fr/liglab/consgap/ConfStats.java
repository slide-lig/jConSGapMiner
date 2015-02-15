package fr.liglab.consgap;

import java.util.concurrent.atomic.AtomicLong;

public class ConfStats {
	public static String separator = "\t";
	public static int nbThreads;
	public static boolean benchmark;
	public static boolean pruneBackspace;
	public static boolean pruneContainsEmerging;
	public static boolean breadthFirst;
	public static boolean minimizeDuringExec;
	public static int minimizeThreshold;
	private static final AtomicLong backspacePruningCount = new AtomicLong(0);
	private static final AtomicLong containsEmergingPruningCount = new AtomicLong(0);
	private static final AtomicLong expandCount = new AtomicLong(0);
	private static final AtomicLong seedItemStarted = new AtomicLong(0);
	private static final AtomicLong emergingPruningCount = new AtomicLong(0);

	public static void checkConfCoherent() {
		if (pruneBackspace) {
			if (!breadthFirst) {
				if (minimizeDuringExec || pruneContainsEmerging) {
					throw new RuntimeException(
							"can't minimize during exec or prune if contains emerging when pruning backspace and going depth first");
				}
			}
		}
		if (breadthFirst && minimizeDuringExec) {
			throw new RuntimeException("no need to minimize during exec when you're going breadthfirst");
		}
	}

	public static void incBackspacePruning() {
		backspacePruningCount.incrementAndGet();
	}

	public static void incContainsEmergingPruning() {
		containsEmergingPruningCount.incrementAndGet();
	}

	public static void incExpand() {
		expandCount.incrementAndGet();
	}

	public static void incSeedItemStarted() {
		seedItemStarted.incrementAndGet();
	}

	public static void incEmergingPruning() {
		emergingPruningCount.incrementAndGet();
	}

	public static void printShortStats() {
		System.out.println("backspacePruningCount: " + backspacePruningCount.get() + " containsEmergingPruningCount: "
				+ containsEmergingPruningCount.get() + " expandCount: " + expandCount.get() + " seedItemStarted: "
				+ seedItemStarted.get() + " emergingPruningCount: " + emergingPruningCount.get());
	}

	public static void startStatsThread() {
		Thread t = new Thread() {

			@Override
			public void run() {
				while (true) {
					try {
						sleep(5 * 60 * 1000);// 5 minutes
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					printShortStats();
				}
			}

		};
		t.setDaemon(true);
		t.start();
	}
}
