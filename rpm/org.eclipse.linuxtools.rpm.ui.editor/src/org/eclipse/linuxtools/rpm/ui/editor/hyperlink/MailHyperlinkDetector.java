/*******************************************************************************
 * Copyright (c) 2008 Alphonse Van Assche.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alphonse Van Assche - initial API and implementation
 *******************************************************************************/

package org.eclipse.linuxtools.rpm.ui.editor.hyperlink;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.linuxtools.rpm.ui.editor.SpecfileEditor;
import org.eclipse.linuxtools.rpm.ui.editor.parser.Specfile;


/**
 * Mail hyperlink detector. Largely inspired of {@link URLHyperlinkDetector}
 */
public class MailHyperlinkDetector extends AbstractHyperlinkDetector {

	private SpecfileEditor editor;

	public MailHyperlinkDetector(SpecfileEditor editor) {
		this.editor = editor;
	}

	/*
	 * @see org.eclipse.jface.text.hyperlink.IHyperlinkDetector#detectHyperlinks(org.eclipse.jface.text.ITextViewer,
	 *      org.eclipse.jface.text.IRegion, boolean)
	 */
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		if (region == null || textViewer == null)
			return null;

		IDocument document= textViewer.getDocument();

		int offset= region.getOffset();

		String urlString= null;
		if (document == null)
			return null;

		IRegion lineInfo;
		String line;
		String mail;
		int mailLength = 0;
		int mailOffsetInLine;
		try {
			lineInfo= document.getLineInformationOfOffset(offset);
			line= document.get(lineInfo.getOffset(), lineInfo.getLength());
		} catch (BadLocationException ex) {
			ex.printStackTrace();
			return null;
		}
		
		int startSeparator= line.indexOf("<"); //$NON-NLS-1$
		mailOffsetInLine = startSeparator + 1;

		if (startSeparator != -1) {
			
			int endSeparator= line.indexOf(">");
			
			if (endSeparator < 5)
				return null;
			
			mail= line.substring(startSeparator + 1, endSeparator).trim();
			mailLength= mail.length();
			
			// Some cleanups, maybe we can add more.
			mail= mail.replaceAll("(?i) at ", "@");
			mail= mail.replaceAll("(?i) dot ", ".");
			mail= mail.replaceAll("(?i)_at_", "@");
			mail= mail.replaceAll("(?i)_dot_", ".");
			
			mail= mail.replaceAll(" +", " ");
			if (mail.split(" ").length == 3) {
				if (mail.indexOf("@") == -1)
					mail = mail.replaceFirst(" ", "@").replaceFirst(" ", ".");
			}
			mail= mail.replaceAll(" ", "");
			
		} else {

			int offsetInLine= offset - lineInfo.getOffset();

			boolean startDoubleQuote= false;
			mailOffsetInLine= 0;

			int mailSeparatorOffset= line.indexOf("@"); //$NON-NLS-1$
			while (mailSeparatorOffset >= 0) {

				// (left to "@")
				mailOffsetInLine= mailSeparatorOffset;
				char ch;
				do {
					mailOffsetInLine--;
					ch= ' ';
					if (mailOffsetInLine > -1)
						ch= line.charAt(mailOffsetInLine);
					startDoubleQuote= ch == '"';
				} while (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '-');
				mailOffsetInLine++;

				// a valid mail contain a left part.
				if (mailOffsetInLine == mailSeparatorOffset)
					return null;

				// Right to "@"
				StringTokenizer tokenizer= new StringTokenizer(line.substring(mailSeparatorOffset + 3), " \t\n\r\f<>", false); //$NON-NLS-1$
				if (!tokenizer.hasMoreTokens())
					return null;

				mailLength= tokenizer.nextToken().length() + 3 + mailSeparatorOffset - mailOffsetInLine;
				if (offsetInLine >= mailOffsetInLine && offsetInLine <= mailOffsetInLine + mailLength)
					break;

				mailSeparatorOffset= line.indexOf("@", mailSeparatorOffset + 1); //$NON-NLS-1$
			}

			if (mailSeparatorOffset < 0)
				return null;

			if (startDoubleQuote) {
				int endOffset= -1;
				int nextDoubleQuote= line.indexOf('"', mailOffsetInLine);
				int nextWhitespace= line.indexOf(' ', mailOffsetInLine);
				if (nextDoubleQuote != -1 && nextWhitespace != -1)
					endOffset= Math.min(nextDoubleQuote, nextWhitespace);
				else if (nextDoubleQuote != -1)
					endOffset= nextDoubleQuote;
				else if (nextWhitespace != -1)
					endOffset= nextWhitespace;
				if (endOffset != -1)
					mailLength= endOffset - mailOffsetInLine;
			}
			if (mailLength == 0)
				return null;
			
			mail= line.substring(mailOffsetInLine, mailOffsetInLine + mailLength);
		}

		try {
			// mail address contain at less one '@' and one '.' character.
			if (!mail.contains("@") || !mail.contains("."))
				return null;			
			
			urlString= "mailto:" + mail;
			char separator= '?';
			String subject= getSubject();
			if (subject != null) {
				urlString+= separator + "subject=" + subject;
				separator= '&';
			}
			String body= getBody();
			if (body != null)
				urlString+= separator + "body=" + body;

			// url don't like %
			urlString= urlString.replaceAll("\\%", "\\%25");
			new URL(urlString);
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
			urlString= null;
			return null;
		}

		IRegion urlRegion= new Region(lineInfo.getOffset() + mailOffsetInLine, mailLength);
		return new IHyperlink[] {new MailHyperlink(urlRegion, urlString)};
	}	

	private String getSubject() {
		Specfile specfile= editor.getSpecfile();
		return "[" + specfile.getName() + ".spec - " + specfile.getVersion() + "-" + specfile.getRelease() + "]";		
	}

	private String getBody() {
		String body = null;
		// Get current selection
		IDocument document= editor.getSpecfileSourceViewer().getDocument();
		ISelection currentSelection= editor.getSpecfileSourceViewer().getSelection();
		if (currentSelection instanceof ITextSelection) {
			ITextSelection selection= (ITextSelection) currentSelection;
			try {
				String txt= selection.getText();
				if (txt.trim().length() > 0) {
					int begin= document.getLineOffset(selection.getStartLine());
					body= document.get().substring(begin,
							selection.getOffset() + selection.getLength());
					// replace left spaces or tabs and add a space at the begin of each line.
					body= body.replaceAll("(?m)^[ \\t]+|[ \\t]+$|^", " ");
				}
			} catch (BadLocationException e) {
			}

		}
		return body;
	}

}
