package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.Activator;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.WriteNode;

public class CWriteNode extends TreeNode{
	public String name;
	public WriteNode write;
	public TIDERace race;
	public HashMap<String, IFile> event_ifile_map;
	public HashMap<String, Integer> event_line_map;

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
		createChild(idx);
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
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub

	}

	@SuppressWarnings("unchecked")
	private void createChild(int idx) {
		String name = "Trace of " + this.name + " is :";
		SubTraceNode subtrace = new SubTraceNode(this, name, race.traceMsg.get(idx - 1));
		super.children.add(subtrace);
	}

//	@SuppressWarnings("unchecked")
//	protected void createChildren(LinkedList<String> trace) {
//		String name = "Trace of " + this.read.getSig() + " is :";
//		SubTraceNode subtrace = new SubTraceNode(this, name, trace);
//		super.children.add(subtrace);
//	}

}
