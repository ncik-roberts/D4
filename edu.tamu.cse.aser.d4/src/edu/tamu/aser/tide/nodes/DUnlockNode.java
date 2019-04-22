package edu.tamu.aser.tide.nodes;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.util.intset.OrdinalSet;

public class DUnlockNode implements SyncNode {

	final int TID;
	int sourceLineNum;
	String lock, instSig;
	PointerKey key;
	OrdinalSet<InstanceKey> instances;
	private Set<String> locksigs = new HashSet<>();
	private CGNode node;

	public DUnlockNode(int curTID, String instSig2, int sourceLineNum, PointerKey key,
			OrdinalSet<InstanceKey> instances, CGNode node, int sln) {
		this.TID = curTID;
		this.instSig = instSig2;
		this.sourceLineNum = sourceLineNum;
		this.key = key;
		this.instances = instances;
		this.node = node;
		this.sourceLineNum = sln;
	}

	public CGNode getBelonging(){
		return node;
	}

	public PointerKey getPointer(){
		return key;
	}

	public void addLockSig(String sig){
		locksigs.add(sig);
	}

	public Set<String> getLockSig(){
		return locksigs;
	}

	public int hashCode(){
		return Objects.hash(locksigs, key);
	}

	public boolean equals(Object o){
		if (!(o instanceof DUnlockNode)) return false;
		DUnlockNode d = (DUnlockNode) o;
		return locksigs.equals(d.locksigs) && instSig.equals(d.instSig);
	}

	public String getLockedObj() {
		return lock;
	}

	public int getTID() {
		return TID;
	}

	public String getLockString(){
		return lock;
	}

	public String toString(){
		String methodname = node.getMethod().getName().toString();
		return "UnLock in " + instSig.substring(0, instSig.indexOf(':')) +"." + methodname;
	}

	@Override
	public int getSelfTID() {
		return TID;
	}

	@Override
	public IFile getFile() {
		return null;
	}

	@Override
	public int getLine() {
		return sourceLineNum;
	}
}
