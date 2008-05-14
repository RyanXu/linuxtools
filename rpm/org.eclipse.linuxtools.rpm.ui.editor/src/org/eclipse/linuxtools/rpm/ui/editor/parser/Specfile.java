/*******************************************************************************
 * Copyright (c) 2007 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial API and implementation
 *    Alphonse Van Assche
 *******************************************************************************/

package org.eclipse.linuxtools.rpm.ui.editor.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

public class Specfile {
	String name = "";

	int epoch = -1;

	String version;

	String release;
	
	String license;
	
	SpecfilePreamble preamble;
	
	SpecfilePackageContainer packages;

	List<SpecfileSection> sections;
	List<SpecfileSection> complexSections;

	Map<String, SpecfileDefine> defines;

	Map<Integer, SpecfileSource> sources;

	Map<Integer, SpecfileSource> patches;

	private IDocument document;

	public Specfile() {
		packages = new SpecfilePackageContainer();
		preamble = new SpecfilePreamble();
		sections = new ArrayList<SpecfileSection>();
		complexSections = new ArrayList<SpecfileSection>();
		defines = new HashMap<String, SpecfileDefine>();
		sources = new HashMap<Integer, SpecfileSource>();
		patches = new HashMap<Integer, SpecfileSource>();
	}

	public Specfile(String name) {
		this();
		this.name = name;
	}

	public Object[] getSections() {
		return sections.toArray();
	}

	public List<SpecfileSection> getSectionsAsList() {
		return sections;
	}
	
	public SpecfileElement[] getSectionsElements() {
		SpecfileElement[] elements = new SpecfileElement[sections.size()]; 
		for (int i = 0 ; i < sections.size(); i++) {
			elements[i] = sections.get(i);
		}
		return elements;
	}
	
	public Object[] getComplexSections() {
		return complexSections.toArray();
	}
	
	public List<SpecfileSection> getComplexSectionsAsList() {
		return complexSections;
	}
	
	public SpecfileElement[] getComplexSectionsElements() {
		SpecfileElement[] elements = new SpecfileElement[complexSections.size()]; 
		for (int i = 0 ; i < complexSections.size(); i++) {
			elements[i] = complexSections.get(i);
		}
		return elements;
	}	

	public SpecfileSource getPatch(int number) {
		return patches.get(new Integer(number));
	}

	public SpecfileSource getSource(int number) {
		return sources.get(new Integer(number));
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void addSection(SpecfileSection section) {
		sections.add(section);
	}

	public void addComplexSection(SpecfileSection section) {
		complexSections.add(section);
	}
	
	public void addSource(SpecfileSource source) {
		sources.put(new Integer(source.getNumber()), source);
	}

	public void addPatch(SpecfileSource patch) {
		patches.put(new Integer(patch.getNumber()), patch);
	}

        // FIXME: This should instantiate a SpecFileDefine from 2 arguments
        // so that you don't have to do 
        // specfile.addDefine( new SpecfileDefine("blah", "bleh", specfile))
	public void addDefine(SpecfileDefine define) {
		defines.put(define.getName(), define);
	}

	public SpecfileDefine getDefine(String defineName) {
		return defines.get(defineName);
	}

	public int getEpoch() {
		return epoch;
	}

	public void setEpoch(int epoch) {
		this.epoch = epoch;
	}

	public String getRelease() {
		return release;
	}

	public void setRelease(String release) {
		this.release = release;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Map<Integer, SpecfileSource> getPatches() {
		return patches;
	}

	public Map<Integer, SpecfileSource> getSources() {
		return sources;
	}

	public Collection<SpecfileSource> getPatchesAsList() {
		List<SpecfileSource> patchesList = new ArrayList<SpecfileSource>(patches.values());
		Collections.sort(patchesList, new SourceComparator());
		return patchesList;
	}

	public Collection<SpecfileSource> getSourcesAsList() {
		List<SpecfileSource> sourcesList = new ArrayList<SpecfileSource>(sources.values());
		Collections.sort(sourcesList, new SourceComparator());
		return sourcesList;
	}

	public SpecfileSource[] getPatchesAsArray() {
		return getPatchesAsList().toArray(new SpecfileSource[patches.size()]);
	}
	
	public Collection<SpecfileDefine> getDefinesAsList() {
		List<SpecfileDefine> definesList = new ArrayList<SpecfileDefine>(defines.values());
		return definesList;
	}
	
	public SpecfileDefine[] getDefinesAsArray() {
		return getDefinesAsList().toArray(new SpecfileDefine[defines.size()]);
	}

	public SpecfileSource[] getSourcesAsArray() {
		return getSourcesAsList().toArray(new SpecfileSource[sources.size()]);
	}

	private void printArray(Object[] array) {
		for (int i = 0; i < array.length; i++) {
			System.out.println(array[i]);
		}
	}

	public void organizePatches() {
		SpecfileSource[] patches = getPatchesAsArray();
//		System.out.println("*** Then:");
//		printArray(patches);
		int newPatchNumber = 0;
		int oldPatchNumber = -1;
		Map<Integer, SpecfileSource> newPatches = new HashMap<Integer, SpecfileSource>();
		for (SpecfileSource thisPatch: patches) {
			if (thisPatch.getSpecfile() == null)
				thisPatch.setSpecfile(this);
			// System.out.println("thisPatch.specfile -> " +
			// thisPatch.getSpecfile() + " ?=? " + this);
			oldPatchNumber = thisPatch.getNumber();
			thisPatch.setNumber(newPatchNumber);
			thisPatch.changeDeclaration(oldPatchNumber);
			thisPatch.changeReferences(oldPatchNumber);
			newPatches.put(new Integer(newPatchNumber), thisPatch);
			newPatchNumber++;
		}
		setPatches(newPatches);
//		System.out.println("*** Now:");
//		List newPatchesList = new ArrayList(newPatches.values());
//		Collections.sort(newPatchesList, new SourceComparator());
//		SpecfileSource[] newPatchesArray = (SpecfileSource[]) newPatchesList
//				.toArray(new SpecfileSource[newPatchesList.size()]);
//		printArray(newPatchesArray);
	}

	public void setDocument(IDocument specfileDocument) {
		document = specfileDocument;
	}

	public String getLine(int lineNumber) throws BadLocationException {
		int offset = document.getLineOffset(lineNumber);
		int length = getLineLength(lineNumber);
		String lineContents = document.get(offset, length);
		return lineContents;
	}

	public IDocument getDocument() {
		return document;
	}

	public int getLineLength(int lineNumber) throws BadLocationException {
		int length = document.getLineLength(lineNumber);
		String lineDelimiter = document.getLineDelimiter(lineNumber);
		if (lineDelimiter != null)
			length = length - lineDelimiter.length();
		return length;
	}

	public void changeLine(int lineNumber, String string)
			throws BadLocationException {
		document.replace(document.getLineOffset(lineNumber),
				getLineLength(lineNumber), string);
	}

	public void setPatches(Map<Integer, SpecfileSource> patches) {
		this.patches = patches;
	}

	public void setSources(Map<Integer, SpecfileSource> sources) {
		this.sources = sources;
	}
	
	public String toString() {
		return getName();
	}

	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public SpecfilePackageContainer getPackages() {
		return packages;
	}

	public SpecfileElement getPreamble() {
		return preamble;
	}

	public SpecfilePackage getPackage(String packageName) {
		return getPackages().getPackage(packageName);
	}

	public void addPackage(SpecfilePackage subPackage) {
		if (! packages.contains(subPackage))
			packages.addPackage(subPackage);
	}
}
