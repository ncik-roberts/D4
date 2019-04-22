package edu.tamu.aser.tide.views;

import java.util.List;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.plugin.Activator;

public class BugDetail extends TreeNode{
	protected String name;
	protected RaceDetail raceDetail;
	protected DeadlockDetail deadlockDetail;
	protected RelationDetail relationDetail;

	public BugDetail(TreeNode parent) {
		super(parent);
		this.name = "Bug Detail";
	}

	public void setRaceDetail(RaceDetail raceDetail) {
		this.raceDetail = raceDetail;
		super.children.add(raceDetail);
	}

	public void setDeadlockDetail(DeadlockDetail deadlockDetail) {
		this.deadlockDetail = deadlockDetail;
		super.children.add(deadlockDetail);
	}

	public void setRelation(RelationDetail relation) {
		this.relationDetail = relation;
		super.children.add(relation);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("folder_icon.gif");
	}

	@Override
	protected void createChildren(List<LinkedList<String>> trace, String fix) {
		//children should be set already
	}

	public void clear() {
		if (raceDetail != null) {
			raceDetail.clear();
		}
		if (deadlockDetail != null) {
			deadlockDetail.clear();
		}
		if (relationDetail != null) {
			relationDetail.clear();
		}
	}

}
