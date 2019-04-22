package edu.tamu.aser.tide.nodes;

import com.ibm.wala.ipa.callgraph.CGNode;

public interface INode {
	int getTID();
	CGNode getBelonging();
}
