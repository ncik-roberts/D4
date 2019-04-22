package edu.tamu.aser.tide.views;

import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;

public interface ITreeNode {
	public String getName();
	public ImageDescriptor getImage();
	public List<ITreeNode> getChildren();
	public boolean hasChildren();
	public ITreeNode getParent();
}
