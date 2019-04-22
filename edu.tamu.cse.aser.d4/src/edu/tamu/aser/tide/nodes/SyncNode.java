package edu.tamu.aser.tide.nodes;

import org.eclipse.core.resources.IFile;

public interface SyncNode extends INode {
	int getSelfTID();
	IFile getFile();
	int getLine();
}
