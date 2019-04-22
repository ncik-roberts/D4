package edu.tamu.aser.tide.akkabug;

import java.util.Set;

import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;

public class IncreCheckDatarace extends CheckDatarace{

	public IncreCheckDatarace(String sig, Set<WriteNode> writes, Set<ReadNode> reads) {
		super(sig, writes, reads);
	}

	public Set<WriteNode> getWrites(){
		return super.writes;
	}

	public Set<ReadNode> getReads(){
		return super.reads;
	}

	public String getSig(){
		return super.sig;
	}

}
