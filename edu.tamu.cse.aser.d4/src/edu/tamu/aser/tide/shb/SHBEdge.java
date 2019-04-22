package edu.tamu.aser.tide.shb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.tamu.aser.tide.nodes.INode;

public class SHBEdge {

	private INode node;
	private String cgnode;
	private Set<Integer> tids = new HashSet<>(); // edge tid mapping

	public SHBEdge(INode node, String n) {
		this.node = node;
		this.cgnode = n;
		int tid = node.getTID();
		if (!tids.contains(tid)) {
			tids.add(tid);
		}
	}

	public INode getSource() {
		return node;
	}

	/**
	 * return sig of cgnode
	 * 
	 * @return
	 */
	public String getSink() {
		return cgnode;
	}

	public void includeTid(int tid) {
		tids.add(tid);
	}

	public void includeTids(List<Integer> tids) {
		this.tids.addAll(tids);
	}

	public void removeTid(int tid) {
		if (tids.contains(tid)) {
			tids.remove(tid);
		}
	}

	public Set<Integer> getEdgeTids() {
		return tids;
	}

	public boolean doesIncludeTid(int tid) {
		return tids.contains(tid);
	}

}
