package edu.tamu.aser.tide.akkabug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;


public class CheckDatarace implements ITIDEBug {

	Set<WriteNode> writes;
	Set<ReadNode> reads;
	String sig;

	public CheckDatarace(String sig, Set<WriteNode> writes2, Set<ReadNode> reads2) {
		// TODO Auto-generated constructor stub
		this.sig = sig;
		this.reads = reads2;
		this.writes = writes2;
	}

	public Set<WriteNode> getWrites(){
		return writes;
	}

	public Set<ReadNode> getReads(){
		return reads;
	}

	public String getSig(){
		return sig;
	}

	@Override
	public HashMap<String, IFile> getEventIFileMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addEventIFileToMap(String event, IFile ifile) {
		// TODO Auto-generated method stub

	}

	@Override
	public HashMap<String, Integer> getEventLineMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addEventLineToMap(String event, int line) {
		// TODO Auto-generated method stub

	}

}
