package fr.liglab.consgap.internals;

import fr.liglab.consgap.internals.Dataset.DeadEndException;
import fr.liglab.consgap.internals.Dataset.EmergingExpansionException;
import fr.liglab.consgap.internals.Dataset.EmergingParentException;
import fr.liglab.consgap.internals.Dataset.InfrequentException;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class MiningStep {
	static final public AtomicInteger loopCounts = new AtomicInteger();
	final private Dataset dataset;
	final private AtomicInteger extensionsIndex;
	final private int[] extensions;
	final private TIntSet deniedSiblingsExtensions;

	public MiningStep(Dataset dataset) {
		this.dataset = dataset;
		this.extensions = dataset.getExtensions();
		Arrays.sort(this.extensions);
		this.extensionsIndex = new AtomicInteger();
		this.deniedSiblingsExtensions = new TIntHashSet();
	}

	public MiningStep next() {
		for (int index = this.extensionsIndex.getAndIncrement(); index < extensions.length; index = this.extensionsIndex
				.getAndIncrement()) {
			loopCounts.incrementAndGet();
			final int extension = extensions[index];
			Dataset extDataset = null;
			try {
				extDataset = this.dataset.expand(extension, deniedSiblingsExtensions);
			} catch (EmergingParentException e) {
				return null;
			} catch (EmergingExpansionException e) {
				synchronized (deniedSiblingsExtensions) {
					deniedSiblingsExtensions.add(extension);
				}
			} catch (DeadEndException | InfrequentException e) {
			}
			if (extDataset != null) {
				// if (extDataset.getSequence().length > 3) {
				// mineInThread(extDataset);
				// } else {
				return new MiningStep(extDataset);
				// }
			}
		}
		return null;
	}

	public int getLevel() {
		return this.dataset.getSequence().length;
	}

	@Override
	public String toString() {
		return Arrays.toString(this.dataset.getSequence()) + " " + this.extensionsIndex.get() + "/"
				+ this.extensions.length;
	}

	private static void mineInThread(Dataset dataset) {
		final int[] extensions = dataset.getExtensions();
		Arrays.sort(extensions);
		final TIntSet deniedSiblingsExtensions = new TIntHashSet();
		for (int index = 0; index < extensions.length; index++) {
			loopCounts.incrementAndGet();
			final int extension = extensions[index];
			Dataset extDataset = null;
			try {
				extDataset = dataset.expand(extension, deniedSiblingsExtensions);
			} catch (EmergingParentException e) {
				return;
			} catch (EmergingExpansionException e) {
				synchronized (deniedSiblingsExtensions) {
					deniedSiblingsExtensions.add(extension);
				}
			} catch (DeadEndException | InfrequentException e) {
			}
			if (extDataset != null) {
				mineInThread(extDataset);
			}
		}
	}
}
