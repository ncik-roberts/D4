/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.eclipse.cg.model.WalaProjectCGModel;
import com.ibm.wala.ide.util.JdtPosition;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.InferGraphRoots;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import edu.tamu.aser.tide.akkabug.BugHub;
import edu.tamu.aser.tide.akkabug.BugWorker;
import edu.tamu.aser.tide.marker.BugMarker;
import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.MethodNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.SyncNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;
import edu.tamu.aser.tide.shb.SHBEdge;
import edu.tamu.aser.tide.shb.SHBGraph;
import edu.tamu.aser.tide.tests.ReproduceBenchmarks;
import edu.tamu.aser.tide.views.EchoDLView;
import edu.tamu.aser.tide.views.EchoRaceView;
import edu.tamu.aser.tide.views.EchoReadWriteView;
import edu.tamu.wala.increpta.callgraph.impl.IPAExplicitCallGraph;
import edu.tamu.wala.increpta.callgraph.impl.IPAExplicitCallGraph.IPAExplicitNode;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAPointerAnalysisImpl;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAPropagationGraph;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAPropagationSystem;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPASSAPropagationCallGraphBuilder;

public class TIDECGModel extends WalaProjectCGModel {

	public AnalysisCache getCache() {
		return (AnalysisCache) engine.getCache();
	}

	public AnalysisOptions getOptions() {
		return engine.getOptions();
	}

	private String entrySignature;

	//start bug akka system
	int nrOfWorkers = 8;
	public ActorSystem akkasys;
	public ActorRef bughub;
	public static TIDEEngine bugEngine;
	private static ClassLoader ourClassLoader = ActorSystem.class.getClassLoader();
	private final static boolean DEBUG = false;

	//gui view
	public EchoRaceView echoRaceView;
	public EchoDLView echoDLView;
	public EchoReadWriteView echoRWView;
	private Map<String, Set<IMarker>> bug_marker_map = new HashMap<>();


	public TIDECGModel(IJavaProject project, String exclusionsFile, String mainMethodSignature) throws IOException, CoreException {
		super(project, exclusionsFile);
		this.entrySignature = mainMethodSignature;
		echoRaceView = (EchoRaceView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echoraceview");
		echoDLView = (EchoDLView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echodlview");
		echoRWView = (EchoReadWriteView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echotableview");
	}

	public EchoRaceView getEchoRaceView() {
		return echoRaceView;
	}

	public EchoReadWriteView getEchoRWView() {
		return echoRWView;
	}

	public EchoDLView getEchoDLView() {
		return echoDLView;
	}

	public TIDEEngine getBugEngine() {
		return bugEngine;
	}

	public void detectBug() {
	    Thread.currentThread().setContextClassLoader(ourClassLoader);
		akkasys = ActorSystem.create("bug");
		IPASSAPropagationCallGraphBuilder builder = (IPASSAPropagationCallGraphBuilder) getCallGraphBuilder();
		try {
			builder.makeCallGraph(getOptions());
		} catch (CancelException e) {
			throw new RuntimeException(e);
		}
		IPAPropagationGraph flowgraph = builder.getSystem().getPropagationGraph();
		//initial bug engine
		bughub = akkasys.actorOf(Props.create(BugHub.class, nrOfWorkers), "bughub");
		bugEngine = new TIDEEngine(builder, entrySignature, callGraph, flowgraph, (IPAPointerAnalysisImpl) builder.getPointerAnalysis(), bughub);
		BugHub.setPlugin(true);
		BugWorker.setPlugin(true);
		bugEngine.setChange(false);
		//detect bug
		bugEngine.detectBothBugs(null);
	}

	public void detectBugAgain(Set<CGNode> changedNodes, Set<CGNode> changedModifiers, Set<CGNode> updateIRNodes, boolean ptachanges) {
		//update bug engine
		bugEngine.setChange(true);
		bugEngine.updateEngine(changedNodes, changedModifiers, updateIRNodes, ptachanges, null);
	}

	public void ignoreCGNodes(Set<CGNode> ignoreNodes) {
		bugEngine.setChange(true);
		bugEngine.ignoreCGNodes(ignoreNodes);
	}

	public void considerCGNodes(Set<CGNode> considerNodes) {
		bugEngine.setChange(true);
		bugEngine.considerCGNodes(considerNodes);
	}

	public void updateGUI(IJavaProject project, IFile file, boolean initial) {
		try{
			if (initial) {
				//remove all markers in previous checks
				IMarker[] markers0 = project.getResource().findMarkers(BugMarker.TYPE_SCARIEST, true, 3);
				IMarker[] markers1 = project.getResource().findMarkers(BugMarker.TYPE_SCARY, true, 3);
				for (IMarker marker : markers0) {
					marker.delete();
				}
				for (IMarker marker : markers1) {
					marker.delete();
				}
				//create new markers
				IPath fullPath = file.getProject().getFullPath();//full path of the project
				if (bugEngine.races.isEmpty() && bugEngine.deadlocks.isEmpty())
					System.err.println(" _________________NO BUGS ________________");
				else
					System.err.println("\nRaces: " + bugEngine.races.size() + "    Deadlocks: " + bugEngine.deadlocks.size() + "\n");

				for(TIDERace race: bugEngine.races) {
					showRace(fullPath, race);
				}
				for(TIDEDeadlock dl: bugEngine.deadlocks) {
					showDeadlock(fullPath, dl);
				}
				initialEchoView();
			}else{
				Set<TIDERace> removedraces = bugEngine.removedraces;
				Set<TIDERace> addedraces = bugEngine.addedraces;
				Set<TIDEDeadlock> removeddeadlocks = bugEngine.removeddeadlocks;
				Set<TIDEDeadlock> addeddeadlocks = bugEngine.addeddeadlocks;
				if (removedraces.isEmpty() && addedraces.isEmpty()
						&& removeddeadlocks.isEmpty() && addeddeadlocks.isEmpty())
					return;
				//remove deleted markers
				for (TIDERace race : removedraces) {
					String key = race.raceMsg;
					removeMarkerForBug(key);
				}
				for (TIDEDeadlock deadlock : removeddeadlocks) {
					String key = deadlock.deadlockMsg;
					removeMarkerForBug(key);
				}
				//show up new markers
				IPath fullPath = file.getProject().getFullPath();//full path of the project
				for (TIDERace add : addedraces) {
					showRace(fullPath, add);
				}
				for (TIDEDeadlock add : addeddeadlocks) {
					showDeadlock(fullPath, add);
				}
				updateEchoView();
			}

		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void removeMarkerForBug(String key) {
		Set<IMarker> markers = bug_marker_map.get(key);
		if (markers != null) {
			for (IMarker marker : markers) {
				try {
					IMarker[] dels = new IMarker[1];
					dels[0] = marker;
					IWorkspace workspace = marker.getResource().getWorkspace();
					workspace.deleteMarkers(dels);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
			bug_marker_map.remove(key);
		}
	}

	/**
	 * we only consider races currently
	 * @param considerbugs
	 * @param file
	 * @throws CoreException
	 */
	public void addBugMarkersForConsider(Set<TIDERace> considerbugs, IFile file) throws CoreException {
		IPath fullPath = file.getProject().getFullPath();//full path of the project
		if (considerbugs == null) {
			considerbugs = bugEngine.addedraces;
		}
		for (TIDERace add : considerbugs) {
				showRace(fullPath, add);
		}
		updateEchoView();
	}

	/**
     * we only consider races currently
	 * @param removedbugs
	 */
	public void removeBugMarkersForIgnore(Set<TIDERace> removedbugs) {
		for (TIDERace race : removedbugs) {
			String key = race.raceMsg;
			removeMarkerForBug(key);
		}
	}

	private void asyncExec(long delayMillis, Runnable r) {
		new Thread(() -> {
			try {
				Thread.sleep(delayMillis);
			} catch (InterruptedException e) {
				// swallow
				e.printStackTrace();
			}
			Display.getDefault().asyncExec(r);
		}).start();
	}

	private void initialEchoView() {
		asyncExec(10, () -> echoRaceView.initialGUI(bugEngine.races));
		asyncExec(10, () -> echoDLView.initialGUI(bugEngine.deadlocks));
		asyncExec(10, () -> echoRWView.initialGUI(bugEngine.races));
	}

	private void updateEchoView() {
		asyncExec(100, () -> echoRaceView.updateGUI(bugEngine.addedraces, bugEngine.removedraces));
		asyncExec(100, () -> echoDLView.updateGUI(bugEngine.addeddeadlocks, bugEngine.removeddeadlocks));
		asyncExec(100, () -> echoRWView.updateGUI(bugEngine.addedraces, bugEngine.removedraces));
	}

	public void updateEchoViewForIgnore() {
		asyncExec(100, () -> echoRaceView.ignoreBugs(bugEngine.removedraces));
		asyncExec(100, () -> echoRWView.ignoreBugs(bugEngine.removedraces));
		asyncExec(100, () -> echoDLView.ignoreBugs(bugEngine.removeddeadlocks));
	}

	public void updateEchoViewForConsider() {
		asyncExec(100, () -> echoRaceView.considerBugs(bugEngine.addedraces));
		asyncExec(200, () -> echoRWView.considerBugs(bugEngine.addedraces));
		asyncExec(200, () -> echoDLView.considerBugs(bugEngine.addeddeadlocks)); //currently, not consider
	}

	private void showDeadlock(IPath fullPath, TIDEDeadlock bug) throws CoreException {
		DLockNode l11 = bug.lp1.lock1;
		DLockNode l12 = bug.lp1.lock2;
		DLockNode l21 = bug.lp2.lock1;
		DLockNode l22 = bug.lp2.lock2;

		String sig11 = l11.getInstSig();
		String sig12 = l12.getInstSig();
		String sig21 = l21.getInstSig();
		String sig22 = l22.getInstSig();

		int line11 = l11.getLine();
		int line12 = l12.getLine();
		int line21 = l21.getLine();
		int line22 = l22.getLine();

		String deadlockMsg = "Deadlock: ("+sig11  +","+sig12+ ";  "+sig21+ ","+sig22+ ")";
		deadlockMsg.replace("$", "");
		System.err.println(deadlockMsg);
		List<LinkedList<String>> traceMsg = obtainTraceOfDeadlock(bug);
//		String fixMsg = obtainFixOfDeadlock(bug);
		bug.setBugInfo(deadlockMsg, traceMsg, null);

		IMarker marker1 = null;
		IMarker marker2 = null;
		IMarker marker3 = null;
		IMarker marker4 = null;
		IFile file11 = l11.getFile();
		if (file11 == null) {
			marker1 = getFileFromSigDL(fullPath,sig11,traceMsg.get(0), line11, deadlockMsg);
		}else{
			marker1 = createMarkerDL(file11,line11,deadlockMsg);
		}
		IFile file12 = l12.getFile();
		if (file12 == null) {
			marker2 = getFileFromSigDL(fullPath,sig12,traceMsg.get(1), line12, deadlockMsg);
		}else{
			marker2 = createMarkerDL(file12,line12,deadlockMsg);
		}
		IFile file21 = l21.getFile();
		if (file21 == null) {
			marker3 = getFileFromSigDL(fullPath,sig21,traceMsg.get(2), line21, deadlockMsg);
		}else{
			marker3 = createMarkerDL(file21,line21,deadlockMsg);
		}
		IFile file22 = l22.getFile();
		if (file22 == null) {
			marker4 = getFileFromSigDL(fullPath,sig22,traceMsg.get(3), line22, deadlockMsg);
		}else{
			marker4 = createMarkerDL(file22,line22,deadlockMsg);
		}

		//store
		Set<IMarker> newMarkers = new HashSet<>();
		newMarkers.add(marker1);
		newMarkers.add(marker2);
		newMarkers.add(marker3);
		newMarkers.add(marker4);
		bug_marker_map.put(deadlockMsg, newMarkers);
	}

	private void showRace(IPath fullPath, TIDERace race) throws CoreException {
		String sig = race.sig;
		MemNode rnode = race.node1;
		MemNode wnode = race.node2;
		int findex = sig.indexOf('.');
		int lindex = sig.lastIndexOf('.');
		if (findex!=lindex)
			sig =sig.substring(0, lindex);//remove instance hashcode

		String raceMsg = "Race: "+sig+" ("+rnode.getSig()+", "+wnode.getSig()+")";
		raceMsg.replace("$", "");
		System.err.println(raceMsg + rnode.getObjSig().toString());
		List<LinkedList<String>> traceMsg = obtainTraceOfRace(race);
//		String fixMsg = obtainFixOfRace(race);
		race.setBugInfo(raceMsg, traceMsg, null);

		IMarker marker1 = null;
		IFile file1 = rnode.getFile();
		if (file1 == null) {
			marker1 = getFileFromSigRace(fullPath,rnode.getSig(),traceMsg.get(0), rnode.getLine(), raceMsg);
		}else{
			marker1 = createMarkerRace(file1, rnode.getLine(), raceMsg);
		}

		IMarker marker2 = null;
		IFile file2 = wnode.getFile();
		if (file2 == null) {
			marker2 = getFileFromSigRace(fullPath,wnode.getSig(),traceMsg.get(1), wnode.getLine(), raceMsg);
		}else{
			marker2 = createMarkerRace(file2, wnode.getLine(), raceMsg);
		}

		//store bug -> markers
		Set<IMarker> newMarkers = new HashSet<>();
		newMarkers.add(marker1);
		newMarkers.add(marker2);
		bug_marker_map.put(raceMsg, newMarkers);
	}

	private List<LinkedList<String>> obtainTraceOfRace(TIDERace race) {
		MemNode rw1 = race.node1;
		MemNode rw2 = race.node2;
		int tid1 = race.tid1;
		int tid2 = race.tid2;
		List<LinkedList<String>> traces = new ArrayList<>();
		LinkedList<String> trace1 = obtainTraceOfINode(tid1, rw1, race, 1);
		LinkedList<String> trace2 = obtainTraceOfINode(tid2, rw2, race, 2);
		traces.add(trace1);
		traces.add(trace2);
		return traces;
	}

	private List<LinkedList<String>> obtainTraceOfDeadlock(TIDEDeadlock bug) {
		DLockNode l11 = bug.lp1.lock1;//1
		DLockNode l12 = bug.lp1.lock2;
		DLockNode l21 = bug.lp2.lock1;//1
		DLockNode l22 = bug.lp2.lock2;
		int tid1 = bug.tid1;
		int tid2 = bug.tid2;
		List<LinkedList<String>> traces = new ArrayList<>();
		LinkedList<String> trace1 = obtainTraceOfINode(tid1, l11, bug, 1);
		trace1.add("   >> nested with ");
//		trace1.add(l12.toString());
		//writeDownMyInfo(trace1, l12, bug);
		String sub12 = l12.toString();
		IFile file12 = l12.getFile();
		int line12 = l12.getLine();
		trace1.addLast(sub12);
		bug.addEventIFileToMap(sub12, file12);
		bug.addEventLineToMap(sub12, line12);

		LinkedList<String> trace2 = obtainTraceOfINode(tid2, l21, bug, 2);
		trace2.add("   =>");
//		trace2.add(l22.toString());
		//writeDownMyInfo(trace2, l22, bug);
		String sub22 = l22.toString();
		IFile file22 = l22.getFile();
		int line22 = l22.getLine();
		trace2.addLast(sub22);
		bug.addEventIFileToMap(sub22, file22);
		bug.addEventLineToMap(sub22, line22);

		traces.add(trace1);
		traces.add(trace2);
		return traces;
	}

	private LinkedList<String> obtainTraceOfINode(int tid, INode rw1, ITIDEBug bug, int idx) {
		LinkedList<String> trace = new LinkedList<>();
		TIDEEngine engine;
		if (DEBUG) {
			engine = ReproduceBenchmarks.engine;
		}else{
			engine = TIDECGModel.bugEngine;
		}
		SHBGraph shb = engine.shb;

		writeDownMyInfo(trace, rw1, bug);
		CGNode node = rw1.getBelonging();
		SHBEdge edge = shb.getIncomingEdgeWithTidForShowTrace(node, tid);
		INode parent = null;
		if (edge == null) {
			StartNode startNode = engine.mapOfStartNode.get(tid);
			if (startNode != null) {
				parent = startNode;
				tid = startNode.getParentTID();
			}else{
				return trace;
			}
		}else{
			parent = edge.getSource();
		}
		while(parent != null) {
			writeDownMyInfo(trace, parent, bug);
			CGNode node_temp = parent.getBelonging();
			if (node_temp != null) {
				//this is a kid thread start node
				if (!node.equals(node_temp)) {
					node = node_temp;
					edge = shb.getIncomingEdgeWithTidForShowTrace(node, tid);
					if (edge == null) {
						//run method: tsp
						if (node_temp.getMethod().getName().toString().contains("run")) {
							StartNode startNode = engine.mapOfStartNode.get(tid);
							tid = startNode.getParentTID();
							edge = shb.getIncomingEdgeWithTidForShowTrace(node, tid);
							if (edge == null) {
								break;
							}
						}else
							break;
					}
					parent = edge.getSource();
					if (parent instanceof StartNode) {
						tid = ((StartNode) parent).getParentTID();
					}
				}else{//recursive calls
					Set<SHBEdge> edges = shb.getAllIncomingEdgeWithTid(node, tid);
					for (SHBEdge edge0 : edges) {
						if (!edge.equals(edge0)) {
							parent = edge0.getSource();
							if (parent instanceof StartNode) {
								tid = ((StartNode) parent).getParentTID();
							}
							break;
						}
					}
				}
			}else
				break;
		}
		return trace;
	}

	@SuppressWarnings("unused")
	private void replaceRootCauseForRace(LinkedList<String> trace, INode root, ITIDEBug bug, int idx) {
		//because trace comes into jdk libraries, which cannot create markers in jdk jar,
		//replace root cause to variables in user programs
 		if (root instanceof MethodNode) {
			MethodNode call = (MethodNode) root;
			SSAAbstractInvokeInstruction inst = call.getInvokeInst();
			CGNode rCgNode = call.getBelonging();
			SSAInstruction[] insts = rCgNode.getIR().getInstructions();
			int param = inst.getUse(0);
			if (param != -1) {
				//find root variable
				SSAInstruction rootV = rCgNode.getDU().getDef(param);
				String instSig = null;
				String sig = null;
				int sourceLineNum = -1;
				IFile file = null;
				if (rootV instanceof SSAFieldAccessInstruction) {
					IMethod method = rCgNode.getMethod();
					try {
						if (rCgNode.getIR().getMethod() instanceof IBytecodeMethod) {
							int bytecodeindex = ((IBytecodeMethod<?>) rCgNode.getIR().getMethod()).getBytecodeIndex(inst.iindex);
							sourceLineNum = (int)rCgNode.getIR().getMethod().getLineNumber(bytecodeindex);
						}else{
							SourcePosition position = rCgNode.getMethod().getSourcePosition(inst.iindex);
							sourceLineNum = position.getFirstLine();
							if (position instanceof JdtPosition) {
								file = ((JdtPosition) position).getEclipseFile();
							}
						}
					} catch (InvalidClassFileException e) {
						e.printStackTrace();
					}
					String classname = ((SSAFieldAccessInstruction)rootV).getDeclaredField().getDeclaringClass().getName().toString();
					String fieldname = ((SSAFieldAccessInstruction)rootV).getDeclaredField().getName().toString();
					sig = classname.substring(1)+"."+fieldname;
					String typeclassname = method.getDeclaringClass().getName().toString();
					instSig =typeclassname.substring(1)+":"+sourceLineNum;
				}else {
					System.out.println();
				}
				//replace
				TIDERace race = (TIDERace) bug;
				MemNode node = null;
				if (idx == 1) {
					node = race.node1;
				}else{
					node = race.node2;
				}
				//race
				race.initsig = sig;
				race.setUpSig(sig);
				//node
				node.setLine(sourceLineNum);;
				node.setSig(instSig);
				node.setPrefix(sig);
//				node.setInst(rootV);
				node.setBelonging(rCgNode);
				//trace: replace with a read/write node
				String classname = rCgNode.getMethod().getDeclaringClass().toString();
				String methodname = rCgNode.getMethod().getName().toString();
				String replace = null;
				if (node instanceof ReadNode) {
					replace = "Reading at line " + sourceLineNum + " in " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
				}else if (node instanceof WriteNode) {
					replace =  "Writing at line " + sourceLineNum + " in " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
				}
				trace.removeLast();
				trace.addFirst(replace);
			}else
				System.out.println();
		}else
			System.out.println();
	}

	private boolean writeDownMyInfo(LinkedList<String> trace, INode node, ITIDEBug bug) {
		String sub = null;
		IFile file = null;
		int line = 0;
		if (node instanceof ReadNode) {
			sub = ((ReadNode)node).toString();
			file = ((ReadNode)node).getFile();
			line = ((ReadNode)node).getLine();
		}else if (node instanceof WriteNode) {
			sub = ((WriteNode)node).toString();
			file = ((WriteNode)node).getFile();
			line = ((WriteNode)node).getLine();
		}else if (node instanceof SyncNode) {
			sub = ((SyncNode)node).toString();
			file = ((SyncNode)node).getFile();
			line = ((SyncNode)node).getLine();
		}else if (node instanceof MethodNode) {
			sub = ((MethodNode) node).toString();
			file = ((MethodNode) node).getFile();
			line = ((MethodNode) node).getLine();
		}else if (node instanceof StartNode) {
			sub = ((StartNode) node).toString();
			file = ((StartNode) node).getFile();
			line = ((StartNode) node).getLine();
		}else{
			sub = node.toString();
		}
		trace.addFirst(sub);
		bug.addEventIFileToMap(sub, file);
		bug.addEventLineToMap(sub, line);
		return true;
	}


	private IMarker createMarkerRace(IFile file, int line, String msg) throws CoreException {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(IMarker.LINE_NUMBER, line);
		attributes.put(IMarker.MESSAGE, msg);
		IMarker newMarker = file.createMarker(BugMarker.TYPE_SCARIEST);
		newMarker.setAttributes(attributes);
		return newMarker;
	}

	private IMarker createMarkerDL(IFile file, int line, String msg) throws CoreException{
		//for deadlock markers
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(IMarker.LINE_NUMBER, line);
		attributes.put(IMarker.MESSAGE,msg);
		IMarker newMarker = file.createMarker(BugMarker.TYPE_SCARY);
		newMarker.setAttributes(attributes);
		return newMarker;
	}

	private IMarker getFileFromSigRace(IPath fullPath, String sig, LinkedList<String> trace, int line, String msg) throws CoreException{//":"
		if (sig.contains("java/util/")) {
			Object[] infos = trace.toArray();
			for (int i = infos.length -1; i >= 0; i--) {
				String info = (String) infos[i];
				if (!info.contains("java/util/")) {
					String need = (String) infos[i+1];
					int idx_start = need.lastIndexOf(" ") + 1;
					int idx_end = need.lastIndexOf(".");
					sig = need.substring(idx_start, idx_end);
					int l_start = need.lastIndexOf("line ") + 5;
					int l_end = need.indexOf(" ", l_start);
					String l_str = need.substring(l_start, l_end);
					line = Integer.parseInt(l_str);
					break;
				}
			}
		}
		String name = sig;
		if (sig.contains(":"))
			name = sig.substring(0,sig.indexOf(':'));
		if (name.contains("$"))
			name=name.substring(0, name.indexOf("$"));
		name=name+".java";

		IPath path = fullPath.append("src/").append(name);
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);

		return createMarkerRace(file, line, msg);
	}

	private IMarker getFileFromSigDL(IPath fullPath, String sig, LinkedList<String> trace, int line, String msg) throws CoreException{//":"
		if (sig.contains("java/util/")) {
			Object[] infos = trace.toArray();
			for (int i = infos.length -1; i >= 0; i--) {
				String info = (String) infos[i];
				if (!info.contains("java/util/")) {
					String need = (String) infos[i+1];
					int idx_start = need.lastIndexOf(" ") + 1;
					int idx_end = need.lastIndexOf(".");
					sig = need.substring(idx_start, idx_end);
					break;
				}
			}
		}
		String name = sig;
		if (sig.contains(":"))
			name = sig.substring(0,sig.indexOf(':'));
		if (name.contains("$"))
			name=name.substring(0, name.indexOf("$"));
		name=name+".java";

		IPath path = fullPath.append("src/").append(name);
		//L/ProducerConsumer/src/pc/Consumer.java
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);

		//for deadlock markers
		return createMarkerDL(file, line, msg);
	}

	private Iterable<Entrypoint> entryPoints;
	@Override
	protected Iterable<Entrypoint> getEntrypoints(AnalysisScope analysisScope, IClassHierarchy classHierarchy) {
		if (entryPoints==null) {
			entryPoints = findEntryPoints(classHierarchy,entrySignature);
		}
		return entryPoints;
	}

	public Iterable<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, String entrySignature) {
		final Set<Entrypoint> result = HashSetFactory.make();
		Iterator<IClass> classIterator = classHierarchy.iterator();
		while (classIterator.hasNext()) {
			IClass klass = classIterator.next();
			if (!AnalysisUtils.isJDKClass(klass)) {
				for (IMethod method : klass.getDeclaredMethods()) {
					try {
						if (method.isStatic()&&method.isPublic()
								&&method.getName().toString().equals("main")
								&&method.getDescriptor().toString().equals(ConvertHandler.DESC_MAIN))

							result.add(new DefaultEntrypoint(method, classHierarchy));
						else if (method.isPublic()&&!method.isStatic()
								&&method.getName().toString().equals("run")
								&&method.getDescriptor().toString().equals("()V"))
						{
							if (AnalysisUtils.implementsRunnableInterface(klass) || AnalysisUtils.extendsThreadClass(klass))
							result.add(new DefaultEntrypoint(method, classHierarchy));

						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}

		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				return result.iterator();
			}
		};

	}

	@Override
	protected Collection<CGNode> inferRoots(CallGraph cg) throws WalaException {
		return InferGraphRoots.inferRoots(cg);
	}

	public PointerAnalysis<?> getPointerAnalysis() {
		return engine.getPointerAnalysis();
	}

	public IClassHierarchy getClassHierarchy() {
		return engine.getClassHierarchy();
	}

	public CGNode getOldCGNode(com.ibm.wala.classLoader.IMethod m_old) {
		IPAExplicitCallGraph cg = (IPAExplicitCallGraph)callGraph;
		try {
			return cg.findOrCreateNode(m_old, Everywhere.EVERYWHERE);
		} catch (CancelException e) {
			throw new RuntimeException(e);
		}
	}

	public CGNode updateCallGraph(com.ibm.wala.classLoader.IMethod m_old,
			com.ibm.wala.classLoader.IMethod m, IR ir, DefUse du) {
		try {
			IPAExplicitCallGraph cg = (IPAExplicitCallGraph) getGraph();
			IPAExplicitNode oldNode = (IPAExplicitNode) cg.findOrCreateNode(m_old, Everywhere.EVERYWHERE);
			oldNode.updateMethod(m, ir, du);
			//update call graph key
			cg.updateNode(m_old, m, Everywhere.EVERYWHERE, oldNode);
			//update call site?
			oldNode.clearAllTargets();//clear old targets
			if (getCallGraphBuilder()!=null &&
					getCallGraphBuilder() instanceof IPASSAPropagationCallGraphBuilder) {
				IPASSAPropagationCallGraphBuilder builder = (IPASSAPropagationCallGraphBuilder) getCallGraphBuilder();
				IPAPropagationSystem system = builder.getSystem();
				system.setUpdateChange(true);
				builder.addConstraintsFromChangedNode(oldNode, null);
				do{
					system.solve(null);
				}while(!system.emptyWorkList());
				system.setUpdateChange(false);
			}
			return oldNode;
		} catch (CancelException e) {
			throw new RuntimeException(e);
		}
	}

	public void updatePointerAnalysis(CGNode node, IR irOld, IR ir) {

    	Map<String,SSAInstruction> mapOld = new HashMap<>();
    	Map<String,SSAInstruction> mapNew = new HashMap<>();


        ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg_old = irOld.getControlFlowGraph();

    	for (SSAInstruction inst : irOld.getInstructions()) {
    		if (inst != null) {
    			mapOld.put(inst.toString(), inst);
    		}
    	}
    	for (SSAInstruction inst : ir.getInstructions()) {
    		if (inst!=null) {
    			mapNew.put(inst.toString(), inst);
    		}
    	}

    	Map<SSAInstruction,ISSABasicBlock> deleted = new HashMap<>();

    	for(Map.Entry<String, SSAInstruction> entry : mapOld.entrySet()) {
    		String key = entry.getKey();
  			SSAInstruction inst = entry.getValue();
    		if (!mapNew.containsKey(key)) {
    			if (inst instanceof SSAFieldAccessInstruction
    					|| inst instanceof SSAAbstractInvokeInstruction
    					|| inst instanceof SSAArrayReferenceInstruction) {
        			ISSABasicBlock bb = cfg_old.getBlockForInstruction(inst.iindex);
        			deleted.put(inst, bb);
    			}
    		}
    	}

		engine.updatePointerAnalaysis(node, deleted, irOld);
	}

	public void clearChanges() {
		((IPASSAPropagationCallGraphBuilder) getCallGraphBuilder()).getSystem().clearChanges();
	}


}
