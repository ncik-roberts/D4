package edu.tamu.aser.tide.akkabug;

import java.util.List;

import edu.tamu.aser.tide.shb.Trace;

public class RemoveLocalJob {

	List<Trace> node;

	public RemoveLocalJob(List<Trace> team1) {
		// TODO Auto-generated constructor stub
		this.node = team1;
	}

	public List<Trace> getTeam(){
		return node;
	}

}
