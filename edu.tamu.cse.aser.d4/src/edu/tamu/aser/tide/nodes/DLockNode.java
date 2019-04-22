package edu.tamu.aser.tide.nodes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.intset.OrdinalSet;

public class DLockNode implements SyncNode {

	private final int TID;
	private String lock;

	private final String instSig;
	private int line;
	private final SSAInstruction inst;
	private final PointerKey key;
	private final OrdinalSet<InstanceKey> instances;
	private final Set<String> locksigs = new HashSet<>();
	private final CGNode node;
	private final IFile file;

	public DLockNode(int curTID, String instSig, int sourceLineNum, PointerKey key,
			OrdinalSet<InstanceKey> instances, CGNode node, SSAInstruction createinst, IFile file) {
		this.TID = curTID;
		this.instSig = instSig;
		this.line = sourceLineNum;
		this.key = key;
		this.instances = instances;
		this.node = node;
		this.inst = createinst;
		this.file = file;
	}

	public DLockNode copy(int line) {
		String new_instSig = instSig.substring(0, instSig.lastIndexOf(":") + 1) + line;
		return new DLockNode(TID, new_instSig, line, key, instances, node, inst, file);
	}

	@Override
	public IFile getFile() {
		return file;
	}
	
	public SSAInstruction getInst() {
		return inst;
	}
	
	@Override
	public CGNode getBelonging() {
		return node;
	}

	public PointerKey getPointer() {
		return key;
	}

	public void addLockSig(String sig) {
		locksigs.add(sig);
	}

	public Set<String> getLockSig() {
		return locksigs;
	}

	public void replaceLockSig(Collection<? extends String> new_sigs) {
		this.locksigs.clear();
		this.locksigs.addAll(new_sigs);
	}

	public String getInstSig() {
		return instSig;
	}

	public SSAInstruction getCreateInst() {
		return inst;
	}

	@Override
	public int getTID() {
		return TID;
	}

	public String getLockString() {
		return lock;
	}

	@Override
	public int getLine() {
		return line;
	}

	public boolean setLine(int line) {
		if (this.line != line) {
			this.line = line;
			return true;
		}
		return false;
	}

	public String toString() {
		String classname = node.getMethod().getDeclaringClass().toString();
		String methodname = node.getMethod().getName().toString();

		String cn;
		boolean jdk;
		if (classname.contains("java/util/")) {
			jdk = true;
			cn = classname.substring(classname.indexOf("L") +1, classname.length() -1);
		} else {
			jdk = false;
			cn = classname.substring(classname.indexOf(':') +3, classname.length());
		}

		if (jdk) {
			return String.format("(Ext Lib) Lock in %s.%s (line %d)", cn, methodname, line);
		} else {
			return String.format("Lock in %s.%s (line %d)", cn, methodname, line);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(locksigs, key);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof DLockNode) {
			DLockNode that = (DLockNode) o;
			if (this.node.equals(that.node)
					&& this.instSig.equals(that.instSig)
					&& this.line == that.line)
				return true;
		}
		return false;
	}

	@Override
	public int getSelfTID() {
		return TID;
	}

}
