package edu.tamu.aser.tide.views;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.Activator;

public class CWriteNode extends TreeNode{
	public String name;
	public WriteNode write;
	public TIDERace race;
	public Map<String, IFile> event_ifile_map;
	public Map<String, Integer> event_line_map;

	public CWriteNode(TreeNode parent, String rsig, WriteNode write, TIDERace race, int idx) {
		super(parent);
		if(write.getLocalSig() != null){
			this.name = write.getPrefix() + write.getLocalSig() + " on line " + write.getLine();
		}else{
			this.name = write.getPrefix() + " on line " + write.getLine();
		}
		this.write = write;
		this.race = race;
		this.event_ifile_map = race.event_ifile_map;
		this.event_line_map = race.event_line_map;
//		createChild(idx);
		createChildNoSubTrace(idx);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("write-icon.png");
	}

	@Override
	protected void createChildren(List<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub

	}

	@SuppressWarnings("unused")
	private void createChildNoSubTrace(int idx) {
		LinkedList<String> events = race.traceMsg.get(idx - 1);
		for (String event : events) {
			EventNode eventNode = new EventNode(this, event);
			super.children.add(eventNode);
		}
	}


}
