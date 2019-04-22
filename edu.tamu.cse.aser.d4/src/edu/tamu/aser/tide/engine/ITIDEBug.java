package edu.tamu.aser.tide.engine;

import java.util.Map;

import org.eclipse.core.resources.IFile;

public interface ITIDEBug {
	public abstract Map<String, IFile> getEventIFileMap();
	public abstract void addEventIFileToMap(String event, IFile ifile);
	public abstract Map<String, Integer> getEventLineMap();
	public abstract void addEventLineToMap(String event, int line);
}
