package edu.tamu.aser.tide.akkabug;

import java.util.Map;

public class FindSharedVarJob {

	String sig;
	Map<Integer, Integer> readMap;
	Map<Integer, Integer> writeMap;

	public FindSharedVarJob(String sig, Map<Integer, Integer> hashMap, Map<Integer, Integer> hashMap2) {
		// TODO Auto-generated constructor stub
		this.sig = sig;
		this.readMap = hashMap2;
		this.writeMap = hashMap;
	}

	public String getSig(){
		return sig;
	}

	public Map<Integer, Integer> getReadMap(){
		return readMap;
	}

	public Map<Integer, Integer> getWriteMap(){
		return writeMap;
	}

}
