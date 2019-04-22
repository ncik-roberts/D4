package edu.tamu.aser.tide.views;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.plugin.Activator;

public class FixNode extends TreeNode{
	protected String name;

	public FixNode(TreeNode parent, String fix) {
		super(parent);
		this.name = fix;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("datasheet.gif");
	}

	@Override
	protected void createChildren(List<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub
	}

}
