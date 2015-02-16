package fr.liglab.consgap.dataset.lcmstyle;

import java.util.Arrays;

public class PositionAndProvenance {
	private final int position;
	private final int[] startingPositions;

	public PositionAndProvenance(final int pos) {
		this(pos, null);
	}

	public PositionAndProvenance(final int pos, final int[] startingPositions) {
		this.position = pos;
		this.startingPositions = startingPositions;
	}

	protected final int getPosition() {
		return position;
	}

	protected final int[] getStartingPositions() {
		return startingPositions;
	}

	@Override
	public String toString() {
		return "PositionAndProvenance [position=" + position + ", startingPositions="
				+ Arrays.toString(startingPositions) + "]";
	}

}
