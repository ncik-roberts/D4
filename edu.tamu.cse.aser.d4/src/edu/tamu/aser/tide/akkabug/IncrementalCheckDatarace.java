package edu.tamu.aser.tide.akkabug;

import java.util.Set;

public class IncrementalCheckDatarace {

	private Set<String> checks;

	public IncrementalCheckDatarace(Set<String> interested_rw) {
		this.checks = interested_rw;
	}

	public Set<String> getNodes(){
		return checks;
	}

}
