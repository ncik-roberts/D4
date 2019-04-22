package edu.tamu.aser.tide.akkabug;

import java.util.Map;

public class FindSharedVariable {

	private Map<String, Map<Integer, Integer>> variableReadMap;
	private Map<String, Map<Integer, Integer>> variableWriteMap;

	public FindSharedVariable(Map<String, Map<Integer, Integer>> rsig_tid_num_map,
			Map<String, Map<Integer, Integer>> wsig_tid_num_map) {
		// TODO Auto-generated constructor stub
		this.variableReadMap = rsig_tid_num_map;
		this.variableWriteMap = wsig_tid_num_map;
	}

	public Map<String, Map<Integer, Integer>> getVReadMap(){
		return variableReadMap;
	}

	public Map<String, Map<Integer, Integer>> getVWriteMap() {
		return variableWriteMap;
	}

}
