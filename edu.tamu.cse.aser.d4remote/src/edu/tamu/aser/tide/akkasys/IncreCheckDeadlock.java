package edu.tamu.aser.tide.akkasys;

import edu.tamu.aser.tide.nodes.DLockNode;

public class IncreCheckDeadlock {

	private DLockNode lock;

	public IncreCheckDeadlock(DLockNode lock) {
		this.lock = lock;
	}

	public DLockNode getCheckLock(){
		return lock;
	}

}
