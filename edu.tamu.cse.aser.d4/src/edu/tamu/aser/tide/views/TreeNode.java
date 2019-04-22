package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public abstract class TreeNode implements ITreeNode {
	protected TreeNode parent;
	protected boolean isNewest = false;
	protected List<ITreeNode> children = new ArrayList<>();

	public TreeNode(TreeNode parent) {
		this.parent = parent;
	}

	public boolean hasChildren() {
		return children.size() > 0;
	}

	public ITreeNode getParent() {
		return parent;
	}

	public List<ITreeNode> getChildren() {
		return children;
	}


	public boolean isNewest() {
		return this.isNewest;
	}

	/* subclasses should override this method and add the child nodes */
	protected abstract void createChildren(List<LinkedList<String>> trace, String fix);

}