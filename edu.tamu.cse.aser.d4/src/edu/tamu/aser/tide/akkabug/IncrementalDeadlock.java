package edu.tamu.aser.tide.akkabug;

import java.util.Set;

import edu.tamu.aser.tide.nodes.DLockNode;

public class IncrementalDeadlock {

	private Set<DLockNode> interested_locks;

	public IncrementalDeadlock(Set<DLockNode> interested_l) {
		this.interested_locks = interested_l;
	}

	public Set<DLockNode> getCheckSigs(){
		return interested_locks;
	}

}
