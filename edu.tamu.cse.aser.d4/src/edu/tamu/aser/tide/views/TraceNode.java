package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.Activator;

public class TraceNode extends TreeNode{
	protected String name;
	protected ArrayList<LinkedList<String>> traces;

	public TraceNode(TreeNode parent, ArrayList<LinkedList<String>> traceMsg) {
		super(parent);
		this.traces = traceMsg;
		initialNode();
	}

	private void initialNode() {
		this.name = "Traces of The Bug: ";
		createChildren(traces, null);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("datasheet.gif");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void createChildren(ArrayList<LinkedList<String>> traces, String fix) {
		LinkedList<String> trace1 = traces.get(0);
		LinkedList<String> trace2 = traces.get(1);
		if(super.parent instanceof RaceNode){
//			"Trace of " + rw1.getSig() + " is in thread" + tid1 + ": \n"
			TIDERace race = ((RaceNode) parent).race;
			//1st rwnode
			String name1 = "Trace of " + race.node1.getSig() + " is :";
			SubTraceNode subtrace1 = new SubTraceNode(this, name1, trace1);
			//2nd rwnode
			String name2 = "Trace of " + race.node2.getSig() + " is :";
			SubTraceNode subtrace2 = new SubTraceNode(this, name2, trace2);
			super.children.add(subtrace1);
			super.children.add(subtrace2);
		}else if(super.parent instanceof DeadlockNode){
//			"Trace of 1st lockpair is in thread" + tid1 + " : "
			TIDEDeadlock deadlock = ((DeadlockNode) parent).deadlock;
			//1st lockpair
			String name1 = "Trace of 1st lockpair is :";
			SubTraceNode subtrace1 = new SubTraceNode(this, name1, trace1);
			//2nd lockpair
			String name2 = "Trace of 2nd lockpair is :";
			SubTraceNode subtrace2 = new SubTraceNode(this, name2, trace2);
			super.children.add(subtrace1);
			super.children.add(subtrace2);
		}
	}

}
