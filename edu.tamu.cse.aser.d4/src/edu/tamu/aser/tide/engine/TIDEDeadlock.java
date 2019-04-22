package edu.tamu.aser.tide.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import edu.tamu.aser.tide.nodes.DLPair;

public class TIDEDeadlock implements ITIDEBug {

	public final DLPair lp1;
	public final DLPair lp2;
	public int tid1;
	public int tid2;
	public String deadlockMsg, fixMsg;
	public List<LinkedList<String>> traceMsg;
	public Map<String, IFile> event_ifile_map = new HashMap<>();
	public Map<String, Integer> event_line_map = new HashMap<>();

	public TIDEDeadlock(DLPair lp1, DLPair lp2) {
		this.lp1 = lp1;
		this.lp2 = lp2;
	}

	public TIDEDeadlock(int tid1, DLPair dllp1, Integer tid2, DLPair dllp2) {
		this.lp1 = dllp1;
		this.lp2 = dllp2;
		this.tid1 = tid1;
		this.tid2 = tid2;
	}

	public Set<String> getInvolvedSig() {
		Set<String> result = new HashSet<>();
		Set<String> sig1 = lp1.lock1.getLockSig();
		Set<String> sig2 = lp1.lock2.getLockSig();
		result.addAll(sig1);
		result.addAll(sig2);
		return result;
	}

	public Map<String, Integer> getEventLineMap() {
		return event_line_map;
	}

	public void addEventLineToMap(String event, int line) {
		event_line_map.put(event, line);
	}

	public Map<String, IFile> getEventIFileMap() {
		return event_ifile_map;
	}

	public void addEventIFileToMap(String event, IFile ifile) {
		event_ifile_map.put(event, ifile);// check later
	}

	public void setBugInfo(String deadlockMsg, List<LinkedList<String>> traceMsg2, String fixMsg) {
		this.deadlockMsg = deadlockMsg;
		this.fixMsg = fixMsg;
		this.traceMsg = traceMsg2;
	}

	@Override
	public int hashCode() {
		return lp1.hashCode() + lp2.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TIDEDeadlock) {
			TIDEDeadlock that = (TIDEDeadlock) obj;
			if ((this.lp1.equals(that.lp1) && this.lp2.equals(that.lp2))
					|| (this.lp1.equals(that.lp2) && this.lp2.equals(that.lp1))) {
				return true;
			}
		}
		return false;
	}
}
