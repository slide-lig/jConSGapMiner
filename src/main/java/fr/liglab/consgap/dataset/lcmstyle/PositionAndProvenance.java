package fr.liglab.consgap.dataset.lcmstyle;

import java.util.List;

public class PositionAndProvenance {
	private final int position;
	private final int provenanceFirstIndex; // inclusive
	private final int provenanceLastIndex; // exclusive
	private final List<PositionAndProvenance> correspondingList;

	public PositionAndProvenance(final int pos) {
		this(pos, -1, -1, null);
	}

	public PositionAndProvenance(final int pos, final int provIndex, List<PositionAndProvenance> correspondingList) {
		this(pos, provIndex, provIndex + 1, correspondingList);
	}

	public PositionAndProvenance(final int pos, final int provFirstIndex, final int provLastIndex,
			List<PositionAndProvenance> correspondingList) {
		this.position = pos;
		this.provenanceFirstIndex = provFirstIndex;
		this.provenanceLastIndex = provLastIndex;
		this.correspondingList = correspondingList;
	}

	public int getPosition() {
		return this.position;
	}

	protected final int getProvenanceFirstIndex() {
		return provenanceFirstIndex;
	}

	protected final int getProvenanceLastIndex() {
		return provenanceLastIndex;
	}

	protected final List<PositionAndProvenance> getCorrespondingList() {
		return correspondingList;
	}

	@Override
	public String toString() {
		return "PositionAndProvenance [position=" + position + ", provenanceFirstIndex=" + provenanceFirstIndex
				+ ", provenanceLastIndex=" + provenanceLastIndex + "]";
	}

}
