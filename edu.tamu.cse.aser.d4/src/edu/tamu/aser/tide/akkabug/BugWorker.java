package edu.tamu.aser.tide.akkabug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.intset.MutableIntSet;

import akka.actor.UntypedActor;
import edu.tamu.aser.tide.engine.AstCGNode2;
import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.TIDECGModel;
import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.DLPair;
import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.DUnlockNode;
import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.JoinNode;
import edu.tamu.aser.tide.nodes.LockPair;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.shb.SHBEdge;
import edu.tamu.aser.tide.shb.SHBGraph;
import edu.tamu.aser.tide.shb.Trace;
import edu.tamu.aser.tide.tests.ReproduceBenchmarks;

public class BugWorker extends UntypedActor {

	private static boolean PLUGIN = false;

	public static void setPlugin(boolean b) {
		PLUGIN = b;
	}

	private static TIDEEngine getEngine() {
		return PLUGIN ? TIDECGModel.bugEngine : ReproduceBenchmarks.engine;
	}
	
	// If o is a non-null instance of T, run consumer on the casted value; otherwise, run "orElse".
	private <T> void cast(Object o, Class<T> klass, Consumer<? super T> consumer, Runnable orElse) {
		if (o != null && klass.isInstance(o)) {
			consumer.accept(klass.cast(o));
		} else {
			orElse.run();
		}
	}

	@Override
	public void onReceive(Object message) throws Throwable {
		cast(message, FindSharedVarJob.class, this::processFindSharedVarJob, () ->
		cast(message, RemoveLocalJob.class, this::processRemoveLocalJob, () ->
		cast(message, CheckDatarace.class, this::processCheckDatarace, () ->
		cast(message, CheckDeadlock.class, this::processCheckDeadlock, () ->
		cast(message, IncreRemoveLocalJob.class, this::processIncreRemoveLocalJob, () ->
		cast(message, IncreCheckDeadlock.class, this::processIncreCheckDeadlock, () ->
		cast(message, IncreCheckDatarace.class, this::processIncreCheckDatarace, () ->
		cast(message, IncreRecheckCommonLock.class, this::processIncreRecheckCommonLocks, () -> unhandled(message)))))))));
	}

	private void processIncreCheckDeadlock(IncreCheckDeadlock job) {
		Set<ITIDEBug> bugs = new HashSet<>();
		DLockNode check = job.getCheckLock();
		TIDEEngine engine = getEngine();
		Set<Integer> tids = engine.threadDLLockPairs.keySet();
		for (int ctid : engine.shb.getTrace(check.getBelonging()).getTraceTids()) {
			List<DLPair> dLLockPairs = engine.threadDLLockPairs.get(ctid);
			if (dLLockPairs == null) {
				continue;
			}
			for (DLPair pair : dLLockPairs) {
				if (pair.lock1.equals(check) || pair.lock2.equals(check)) {
					DLPair dllp1 = pair;
					for (int tid2 : tids) {
						if (tid2 != ctid) {
							for (DLPair dllp2 : engine.threadDLLockPairs.get(tid2)) {
								TIDEDeadlock dl = checkDeadlock(dllp1, dllp2, ctid, tid2);
								if (dl != null) {
									bugs.add(dl);
								}
							}
						}
					}
				}
			}
		}
		if (bugs.size() > 0) {
			engine.addBugsBack(bugs);
		}
		getSender().tell(new ReturnResult(), getSelf());
	}

	private TIDEDeadlock checkDeadlock(DLPair dllp1, DLPair dllp2, int tid1, int tid2) {
		Set<String> l11sig = dllp1.lock1.getLockSig();
		Set<String> l12sig = dllp1.lock2.getLockSig();
		Set<String> l21sig = dllp2.lock1.getLockSig();
		Set<String> l22sig = dllp2.lock2.getLockSig();
		if (containsAny(l11sig, l22sig) && containsAny(l21sig, l12sig)) {
			//check reachability
			boolean reached1 = hasHBRelation(tid1, dllp1.lock1, tid2, dllp2.lock1);
			boolean reached2 = hasHBRelation(tid1, dllp1.lock2, tid2, dllp2.lock2);
			if (reached1 && reached2) {
				TIDEDeadlock dl = new TIDEDeadlock(tid1,dllp1, tid2,dllp2);
//				if ((l11sig.equals(l12sig)) || (l21sig.equals(l22sig))) {
//					//maybe reentrant lock, but hard to check
//					PointerKey l11key = dllp1.lock1.getPointer();
//					PointerKey l12key = dllp1.lock2.getPointer();
//					PointerKey l21key = dllp2.lock1.getPointer();
//					PointerKey l22key = dllp2.lock2.getPointer();
//					if (l11key.equals(l12key) || l21key.equals(l22key)) {
//						isReentrant = true;
//					}
//				}
				return dl;
			}
		}
		return null;
	}

	private void processCheckDeadlock(CheckDeadlock job) {
		TIDEEngine engine = getEngine();
		Set<ITIDEBug> bugs = new HashSet<>();
		Set<Integer> tids = job.getTids();
		int tid1 = job.getTid();
		for (DLPair dllp1 : job.getPairs()) {
			for (int tid2 : tids) {
				if (tid2 != tid1) {
					for (DLPair dllp2 : engine.threadDLLockPairs.get(tid2)) {
						TIDEDeadlock dl = checkDeadlock(dllp1, dllp2, tid1, tid2);
						if (dl != null) {
							bugs.add(dl);
						}
					}
				}
			}
		}
		if (bugs.size() > 0) {
			engine.addBugsBack(bugs);
		}
		getSender().tell(new ReturnResult(), getSelf());

	}

	private static boolean containsAny(Set<?> sigs1, Collection<?> sigs2) {
		for (Object sig2 : sigs2) {
			if (sigs1.contains(sig2)) {
				return true;
			}
		}
		return false;
	}

	private void processIncreCheckDatarace(IncreCheckDatarace job) {
		processCheckDatarace(job);
	}

	private void processCheckDatarace(CheckDatarace job) {//tids??
		Set<WriteNode> writes = job.getWrites();
		Set<ReadNode> reads = job.getReads();
		Set<ITIDEBug> bugs = new HashSet<>();
		String sig = job.getSig();
		TIDEEngine engine = getEngine();
		SHBGraph shb = engine.shb;

		//write->read
	    for (WriteNode wnode : job.getWrites()) {
	    	Trace wTrace = shb.getTrace(wnode.getBelonging());
	    	if (wTrace == null) {
				continue;
			}
			List<Integer> wtids = wTrace.getTraceTids();
	    	if (reads != null) {
	    		for (ReadNode read : reads) {
	    			MemNode xnode = read;
	    			Trace xTrace = shb.getTrace(xnode.getBelonging());
	    			if (xTrace == null) {//this xnode shoudl be deleted already!!!!!!
						continue;
					}
					List<Integer> xtids = xTrace.getTraceTids();
					for (int wtid : wtids) {
						for (int xtid : xtids) {
							if (checkLockSetAndHappensBeforeRelation(wtid, wnode, xtid, xnode)) {
								TIDERace race = new TIDERace(sig,xnode,xtid,wnode, wtid);
								bugs.add(race);
							}
						}
					}
	    		}
	    	}
	    }
	    //write -> write
	    WriteNode[] writesArray = writes.toArray(new WriteNode[0]);
	    for (int i = 0; i < writesArray.length; i++) {
	    	WriteNode wnode = writesArray[i];
	    	Trace wTrace = shb.getTrace(wnode.getBelonging());
	    	if (wTrace == null) {
				continue;
			}
			List<Integer> wtids = wTrace.getTraceTids();
			for (int j = i; j < writesArray.length; j++) {
				WriteNode xnode = writesArray[j];
	    		Trace xTrace = shb.getTrace(xnode.getBelonging());
	    		if (xTrace == null) {
	    			continue;
				}
				List<Integer> xtids = xTrace.getTraceTids();
				for (int wtid : wtids) {
					for (int xtid : xtids) {
						if (checkLockSetAndHappensBeforeRelation(xtid, xnode, wtid, wnode)) {
							TIDERace race = new TIDERace(sig,xnode, xtid, wnode, wtid);
							bugs.add(race);
						}
					}
				}
			}
		}

	    if (bugs.size() > 0) {
	    	engine.addBugsBack(bugs);
		}
		getSender().tell(new ReturnResult(), getSelf());
	}
	
	private void processIncreRemoveLocalJob(IncreRemoveLocalJob job) {
		//update later
		String check = job.getCheckSig();
		TIDEEngine engine = getEngine();
		Map<String, Set<ReadNode>> sigReadNodes = new HashMap<>();
		Map<String, Set<WriteNode>> sigWriteNodes = new HashMap<>();
		List<Trace> alltraces = engine.shb.getAllTraces();
		for (Trace trace : alltraces) {
			List<INode> nodes = trace.getContent();
			for (INode node : nodes) {
				if (node instanceof MemNode) {
					Set<String> sigs = ((MemNode)node).getObjSig();
					filterRWNodesBySig(sigs, check, node, sigReadNodes, sigWriteNodes);
				}
			}
		}
		engine.addSigReadNodes(sigReadNodes);
		engine.addSigWriteNodes(sigWriteNodes);
		getSender().tell(new ReturnResult(), getSelf());
	}

	private void filterRWNodesBySig(Set<String> sigs, String sig, INode node,
			Map<String, Set<ReadNode>> sigReadNodes, Map<String, Set<WriteNode>> sigWriteNodes) {
		if (sigs.contains(sig)) {
			if (node instanceof ReadNode) {
				Set<ReadNode> reads = sigReadNodes.get(sig);
				if (reads==null) {
					reads = new HashSet<> ();
					reads.add((ReadNode) node);
					sigReadNodes.put(sig, reads);
				} else {
					reads.add((ReadNode)node);
				}
			} else {//write node
				Set<WriteNode> writes = sigWriteNodes.get(sig);
				if (writes==null) {
					writes = new HashSet<> ();
					writes.add((WriteNode)node);
					sigWriteNodes.put(sig, writes);
				} else {
					writes.add((WriteNode)node);
				}
			}
		}
	}


	private void processRemoveLocalJob(RemoveLocalJob job) {
		List<Trace> team = job.getTeam();
		TIDEEngine engine = getEngine();
		Set<String> sharedFields = engine.sharedFields;
		Map<String, Set<ReadNode>> sigReadNodes = new HashMap<>();
		Map<String, Set<WriteNode>> sigWriteNodes = new HashMap<>();

		for (int i=0; i<team.size(); i++) {
			Trace trace = team.get(i);
			List<INode> nodes = trace.getContent();
			for (INode node : nodes) {
				if (node instanceof MemNode) {
					Set<String> sigs = ((MemNode)node).getObjSig();
					for (String sig : sigs) {
						filterRWNodesBySig(sharedFields, sig, node, sigReadNodes, sigWriteNodes);
					}
				}
			}
		}
		engine.addSigReadNodes(sigReadNodes);
		engine.addSigWriteNodes(sigWriteNodes);
		getSender().tell(new ReturnResult(), getSelf());
	}

	private void processFindSharedVarJob(FindSharedVarJob job) {
		Set<String> sharedFields = new HashSet<>();
		String sig = job.getSig();
		Map<Integer, Integer> readMap = job.getReadMap();
		Map<Integer, Integer> writeMap = job.getWriteMap();
		TIDEEngine engine = getEngine();
		int writeTids = writeMap.size();
		if (writeTids > 1) {
			sharedFields.add(sig);
		} else {
			if (readMap != null) {
				int readTids = readMap.size();
				if (readTids + writeTids > 1) {
					sharedFields.add(sig);
				}
			}
		}
		engine.addSharedVars(sharedFields);
		getSender().tell(new ReturnResult(), getSelf());
	}

	private boolean checkLockSetAndHappensBeforeRelation(Integer wtid, WriteNode wnode, Integer xtid, MemNode xnode) {//ReachabilityEngine reachEngine,
		if (wtid != xtid) {
			if (!hasCommonLock(xtid, xnode, wtid, wnode)) {
				return hasHBRelation(wtid, wnode, xtid, xnode);
			}
//			else if (engine.change) {
//				engine.addRecheckBugs(wnode, xnode);
//			}
		}
		return false;
	}

	private void processIncreRecheckCommonLocks(IncreRecheckCommonLock job) {
		TIDEEngine engine = getEngine();
		Set<TIDERace> group = job.getGroup();
		//has common lock, update the race inside lock
		Set<TIDERace> recheckRaces = engine.recheckRaces;
		Set<TIDERace> removes = new HashSet<>();
		for (TIDERace check : group) {
			for (ITIDEBug bug : recheckRaces) {
				if (bug instanceof TIDERace) {
					TIDERace exist = (TIDERace) bug;
					if (exist.equals(check)) {
						removes.add(check);
					}
				}
			}
		}

		if (removes.size() > 0) {
			engine.removeBugs(removes);
		}
		getSender().tell(new ReturnResult(), getSelf());
	}

	private boolean hasCommonLock(int xtid, INode xnode, int wtid, INode wnode) {
		//inode location
		Map<LockPair, INode> xpair_edge_locations = new HashMap<>();
		Map<LockPair, INode> wpair_edge_locations = new HashMap<>();
		//get all the lockpair on the path
		Map<String, List<LockPair>> xAllPairs = collectAllLockPairsFor(xtid, xnode, xpair_edge_locations);
		Map<String, List<LockPair>> wAllPairs = collectAllLockPairsFor(wtid, wnode, wpair_edge_locations);
		//check common lock
		for (String sig : xAllPairs.keySet()) {
			List<LockPair> xPairs = xAllPairs.get(sig);
			List<LockPair> wPairs = wAllPairs.get(sig);
			if (xPairs!= null && wPairs != null) {
				//TODO: because of 1-objectsensitive, in bubblesort/OneBubble/SwapConsecutives:
				//this check will consider the sync on _threadCounterLock as a common lock => TN
				boolean xhas = doesHaveLockBetween(xnode, xPairs, xpair_edge_locations);
				boolean whas = doesHaveLockBetween(wnode, wPairs, wpair_edge_locations);
				if (xhas && whas) {
					return true;
				}
			}
		}
		return false;
	}

	private Map<String, List<LockPair>> collectAllLockPairsFor(int tid, INode node,
			Map<LockPair, INode> pair_edge_locations) {
		Map<String, List<LockPair>> allPairs = new HashMap<>();
		SHBGraph shb = getEngine().shb;
		//current; recursive
		int round = 5;//can be changed
		while(round >= 0) {
			CGNode cgNode = node.getBelonging();
			Trace trace = shb.getTrace(cgNode);
			if (trace == null) {
				if (cgNode instanceof AstCGNode2) {
					cgNode = ((AstCGNode2) cgNode).getCGNode();
					trace = shb.getTrace(cgNode);
				}
			}
			List<LockPair> pairs = trace.getLockPair();
			for (LockPair pair : pairs) {
				pair_edge_locations.put(pair, node);
				Set<String> sigs = ((DLockNode) pair.lock).getLockSig();
				for (String sig : sigs) {
					List<LockPair> exists = allPairs.get(sig);
					if (exists == null) {
						exists = new ArrayList<>();
						exists.add(pair);
						allPairs.put(sig, exists);
					} else {
						exists.add(pair);
					}
				}
			}
			SHBEdge edge = shb.getIncomingEdgeWithTidForShowTrace(cgNode, tid);//using dfs, since usually is single tid shbedge
			if (edge == null) {
				break;
			} else {
				node = edge.getSource();
				round--;
			}
		}

		return allPairs;
	}



	private boolean doesHaveLockBetween(INode check, List<LockPair> pairs,
			Map<LockPair, INode> pair_edge_locations) {
		SHBGraph shb = getEngine().shb;
		for (LockPair pair : pairs) {
			INode node = pair_edge_locations.get(pair);
			DLockNode lock = (DLockNode) pair.lock;
			DUnlockNode unlock = (DUnlockNode) pair.unlock;
			Trace trace = shb.getTrace(node.getBelonging());
			int idxL = trace.indexOf(lock);
			int idxN = trace.indexOf(node);
			int idxU = trace.indexOf(unlock);
			if (idxL < idxN && idxN < idxU) {
				return true;
			}
		}
		return false;
	}

	private StartNode sameParent(int tid1, int tid2) {
		Map<Integer, StartNode> mapOfStartNode = getEngine().mapOfStartNode;
		for (Map.Entry<Integer, StartNode> entry : mapOfStartNode.entrySet()) {
			int key = entry.getKey();
			StartNode value = entry.getValue();
			if (key != tid1 && key != tid2) {
				MutableIntSet kids = value.getTID_Child();
				if (kids.contains(tid1) && kids.contains(tid2)) {
					return value;
				}
			}
		}
		return null;
	}

	private boolean hasHBRelation(int erTID, INode comper, int eeTID, INode compee) {
		boolean donothave = false;
		TIDEEngine engine = getEngine();
		SHBGraph shb = engine.shb;
		CallGraph cg = engine.callGraph;
		StartNode erStartNode = engine.mapOfStartNode.get(erTID);
		StartNode eeStartNode = engine.mapOfStartNode.get(eeTID);
		JoinNode erJoinNode = engine.mapOfJoinNode.get(erTID);
		JoinNode eeJoinNode = engine.mapOfJoinNode.get(eeTID);

		if (erStartNode == null || eeStartNode == null) {
			return false;//should not be?? the startnode has been removed, but the rwnode still got collected.
		}
		MutableIntSet erkids = erStartNode.getTID_Child();
		MutableIntSet eekids = eeStartNode.getTID_Child();
		// -1: sync -> comper; 1: comper -> sync; 0: ?
		if (erkids.contains(eeTID)) {
			//wtid is parent of xtid, wtid = comper
			if (shb.compareParent(eeStartNode, comper, eeTID, erTID) < 0) {//trace.indexOf(xStartNode) < trace.indexOf(comper)
				if (eeJoinNode != null) {
					if (shb.compareParent(eeJoinNode, comper, eeTID, erTID) > 0) {//trace.indexof(xjoinnode) > trace.indexof(comper)
						donothave = true; //for multipaths: what if the paths compared above are different?
					}
				} else {
					donothave = true;
				}
			}
		} else if (eekids.contains(erTID)) {
			//xtid is parent of wtid, xtid = compee
			if (shb.compareParent(erStartNode, compee, eeTID, erTID) < 0) {//trace.indexOf(wStartNode) < trace.indexOf(compee)
				if (erJoinNode != null) {
					if (shb.compareParent(erJoinNode, compee, eeTID, erTID) > 0) {////trace.indexof(wjoinnode) > trace.indexof(compee)
						donothave = true;
					}
				} else {
					donothave = true;
				}
			}
		} else {
			StartNode sNode = sameParent(erTID, eeTID);
			if (sNode != null) {
				CGNode parent;
				if (sNode.getParentTID() == -1) {
					parent = sNode.getTarget();//main
				} else {
					parent = sNode.getBelonging();
				}
				//same parent
				if (erJoinNode == null && eeJoinNode == null) {
					//should check the distance
					Trace ptTrace = shb.getTrace(parent);//maybe mark the relation??
					int erS = ptTrace.indexOf(erStartNode);
					int eeS = ptTrace.indexOf(eeStartNode);
					if (Math.abs(erS - eeS) <= 1000) {//adjust??
						donothave = true;
					}
				} else if (erJoinNode == null) {//-1: start -> join; 1: join -> start;
					if (shb.compareStartJoin(erStartNode, eeJoinNode, parent, cg) < 0) {//trace.indexOf(xJoinNode) > trace.indexOf(wStartNode)
						donothave = true;
					}
				} else if (eeJoinNode == null) {
					if (shb.compareStartJoin(eeStartNode, erJoinNode, parent, cg) < 0) {//trace.indexOf(wJoinNode) > trace.indexOf(xStartNode)
						donothave = true;
					}
				} else {
					if (shb.compareStartJoin(erStartNode, eeJoinNode, parent, cg) < 0
							&& shb.compareStartJoin(eeStartNode, erJoinNode, parent, cg) < 0) {//(trace.indexOf(xJoinNode) > trace.indexOf(wStartNode)) && (trace.indexOf(wJoinNode) > trace.indexOf(xStartNode))
						donothave = true;
					}
				}
			} else {
				//other conditions??wtid = comper; xtid = compee
				if (shb.whoHappensFirst(erStartNode, eeStartNode, eeTID, erTID) < 0) {//trace.indexOf(wStartNode) < trace.indexOf(xStartNode)
					//wtid starts early
					if (shb.whoHappensFirst(erStartNode, comper, eeTID, erTID) < 0) {
						donothave = true;
					}
				} else {
					//xtid starts early
					if (shb.whoHappensFirst(eeStartNode, compee, eeTID, erTID) < 0) {
						donothave = true;
					}
				}
			}
		}
		return donothave;
	}


}
