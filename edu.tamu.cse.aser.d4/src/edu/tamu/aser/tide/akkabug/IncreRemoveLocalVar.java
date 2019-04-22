package edu.tamu.aser.tide.akkabug;

import java.util.Set;

public class IncreRemoveLocalVar {

	private Set<String> checks;

	public IncreRemoveLocalVar(Set<String> interested_rw) {
		this.checks = interested_rw;
	}

	public Set<String> getNodes() {
		return checks;
	}

}
