package edu.tamu.aser.tide.akkabug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.routing.BalancingPool;
import edu.tamu.aser.tide.engine.TIDECGModel;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.nodes.DLPair;
import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.shb.Trace;
import edu.tamu.aser.tide.tests.ReproduceBenchmarks;

public class BugHub extends UntypedActor {

	private static boolean PLUGIN = false;

	private static boolean finished = false;
	private int nrOfWorks;
	private int nrOfResults;

	private final ActorRef workerRouter;

	public BugHub(final int nrOfWorkers) {
		Props props = Props.create(BugWorker.class).withRouter(new BalancingPool(nrOfWorkers));
		workerRouter = this.getContext().actorOf(props, "bugWorkerRouter");
	}

	public static void setPlugin(boolean b) {
		PLUGIN = b;
	}

	private static TIDEEngine getEngine() {
		return PLUGIN ? TIDECGModel.bugEngine : ReproduceBenchmarks.engine;
	}

	@Override
	public void onReceive(Object message) throws Throwable {
		//parallel build shb
		if (message instanceof FindSharedVariable) {
			FindSharedVariable work = (FindSharedVariable) message;
			Map<String, Map<Integer, Integer>> variableWriteMap = work.getVWriteMap();
			Map<String, Map<Integer, Integer>> variableReadMap = work.getVReadMap();
			for (String key : variableWriteMap.keySet()) {
				FindSharedVarJob job = new FindSharedVarJob(key, variableWriteMap.get(key), variableReadMap.get(key));
				workerRouter.tell(job, getSelf());
			}
			nrOfWorks = variableWriteMap.keySet().size();
		} else if (message instanceof RemoveLocalVar) {//trace, remove local nodes
			TIDEEngine engine = getEngine();
			//constract w/rnodes
			List<Trace> alltrace = engine.shb.getAllTraces();
			int total = alltrace.size();
			int num_in_team = total/8 + total%8;
			List<Trace> team = new ArrayList<>();
			for (Trace trace : alltrace) {
				team.add(trace);
				if (team.size() == num_in_team) {
					List<Trace> team1 = new ArrayList<Trace>();
					team1.addAll(team);
					workerRouter.tell(new RemoveLocalJob(team1), getSelf());
					team.clear();
					nrOfWorks++;
				}
			}
			if (team.size() > 0) {
				workerRouter.tell(new RemoveLocalJob(team), getSelf());
				nrOfWorks++;
			}
		} else if (message instanceof DistributeDatarace) {//parallel check bugs
			TIDEEngine engine = getEngine();
			for (String sig : engine.sharedFields) {
				Set<WriteNode> writes = engine.sigWriteNodes.get(sig);
				if (writes != null) {
					Set<ReadNode> reads = engine.sigReadNodes.get(sig);
					workerRouter.tell(new CheckDatarace(sig, writes, reads), getSelf());
					nrOfWorks++;
				}
			}
			if (nrOfWorks == 0) {
				doWeTerminate();
			}
		} else if (message instanceof DistributeDeadlock) {//parallel check bugs
			TIDEEngine engine = getEngine();
			Set<Integer> tids = engine.threadDLLockPairs.keySet();
			for (Integer tid1: tids) {
				List<DLPair> dLLockPairs = engine.threadDLLockPairs.get(tid1);
				workerRouter.tell(new CheckDeadlock(tid1, tids, dLLockPairs), getSelf());
				nrOfWorks++;
			}
			if (nrOfWorks == 0) {
				doWeTerminate();
			}
		}

		//returned work results
		else if (message instanceof ReturnResult) {
			nrOfResults++;
			doWeTerminate();
		}

		//for incremental
		else if (message instanceof IncrementalDeadlock) {
			IncrementalDeadlock newlocks = (IncrementalDeadlock) message;
			Set<DLockNode> interested_locks = newlocks.getCheckSigs();
			for (DLockNode lock : interested_locks) {
				workerRouter.tell(new IncreCheckDeadlock(lock), getSelf());
				nrOfWorks++;
			}
			if (nrOfWorks == 0) {
				doWeTerminate();
			}
		} else if (message instanceof IncreRemoveLocalVar) {
			IncreRemoveLocalVar work = (IncreRemoveLocalVar) message;
			Set<String> checks = work.getNodes();
			for (String check : checks) {
				workerRouter.tell(new IncreRemoveLocalJob(check), getSelf());
				nrOfWorks++;
			}
			if (nrOfWorks == 0) {
				doWeTerminate();
			}
		} else if (message instanceof IncrementalCheckDatarace) {
			IncrementalCheckDatarace work = (IncrementalCheckDatarace) message;
			Set<String> checks = work.getNodes();
			TIDEEngine engine = getEngine();
			for (String sig : checks) {
				Set<WriteNode> writes = engine.sigWriteNodes.get(sig);
				Set<ReadNode> reads = engine.sigReadNodes.get(sig);
				if (writes != null) {
					if (writes.size() > 0) {
						workerRouter.tell(new IncreCheckDatarace(sig, writes, reads), getSelf());
						nrOfWorks++;
					}
				}
			}
			if (nrOfWorks == 0) {
				doWeTerminate();
			}
		}
//		else if (message instanceof IncrementalRecheckCommonLock) {
//			TIDEEngine engine = getEngine();
//			Object[] bugs = engine.bugs.toArray();
//			int total = bugs.length;
//			int num_in_group = total/8 + total%8;
//			HashSet<TIDERace> group = new HashSet<>();
//			for (int i=0;i<bugs.length;i++) {
//				ITIDEBug bug = (ITIDEBug) bugs[i];
//				if (bug instanceof TIDERace) {
//					group.add((TIDERace) bug);
//					if (group.size() == num_in_group) {
//						HashSet<TIDERace> team1 = new HashSet<>();
//						team1.addAll(group);
//						workerRouter.tell(new IncreRecheckCommonLock(team1), getSelf());
//						group.clear();
//						nrOfWorks++;
//					}
//				}
//			}
//			if (group.size() > 0) {
//				workerRouter.tell(new IncreRecheckCommonLock(group), getSelf());
//				nrOfWorks++;
//			}
//			if (nrOfWorks == 0) {
//				doWeTerminate();
//			}
//		}
		else {
			unhandled(message);
		}
	}

	public static boolean askstatus() {
		if (finished) {
			finished = false;
			return false;
		} else {
			return true;
		}
	}

	private void doWeTerminate() {
		// if all jobs complete
		if (nrOfResults == nrOfWorks) {
//	      System.err.println("num of bug works: " + nrOfWorks);
			// clear this round
			nrOfWorks = 0;
			nrOfResults = 0;
			finished = true;
		}
	}

}
