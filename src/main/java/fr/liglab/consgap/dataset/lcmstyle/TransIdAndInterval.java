package fr.liglab.consgap.dataset.lcmstyle;

import java.util.List;

public class TransIdAndInterval {
	private final int transId;
	private final int provenanceFirstIndex;
	private int provenanceLastIndex;
	private List<PositionAndProvenance> correspondingList;

	public TransIdAndInterval(int transId, int provenanceFirstIndex, int provenanceLastIndex,
			List<PositionAndProvenance> correspondingList) {
		super();
		this.transId = transId;
		this.provenanceFirstIndex = provenanceFirstIndex;
		this.provenanceLastIndex = provenanceLastIndex;
		this.correspondingList = correspondingList;
	}

	public boolean merge(PositionAndProvenance pos) {
		if (pos.getProvenanceFirstIndex() <= this.provenanceLastIndex) {
			this.provenanceLastIndex = Math.max(this.provenanceLastIndex, pos.getProvenanceLastIndex());
			return true;
		} else {
			return false;
		}
	}

	protected final int getTransId() {
		return transId;
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
		return "TransIdAndInterval [transId=" + transId + ", provenanceFirstIndex=" + provenanceFirstIndex
				+ ", provenanceLastIndex=" + provenanceLastIndex + "]";
	}
}
