//
// Copyright (c) 2016 Gianluca Quercini
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
// OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
// ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
//
package org.graphipedia.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import org.graphipedia.GraphipediaSettings;
import org.graphipedia.dataextract.InfoboxTemplatesExtractor;
import org.graphipedia.dataextract.NamespaceExtractor;
import org.graphipedia.progress.LoggerFactory;
import org.graphipedia.progress.ProgressCounter;
import org.graphipedia.progress.ReadableTime;
import org.graphipedia.wikipedia.DisambiguationPages;
import org.graphipedia.wikipedia.Infobox;
import org.graphipedia.wikipedia.InfoboxTemplates;
import org.graphipedia.wikipedia.Introduction;
import org.graphipedia.wikipedia.Namespace;
import org.graphipedia.wikipedia.Namespaces;
import org.graphipedia.wikipedia.parser.InfoboxParser;
import org.graphipedia.wikipedia.parser.IntroductionParser;
import org.graphipedia.wikipedia.parser.SimpleStaxParser;
import org.graphipedia.wikipedia.parser.WikiTextCleaner;
import org.graphipedia.wikipedia.parser.WikiTextParser;
import org.graphipedia.wikipedia.parser.XmlFileTags;

/**
 * This auxiliary program is used to extract the infoboxes of the 
 * Wikipedia pages in a specific language edition.
 * The introductions are written to a CSV file stored in the working directory, where each line contains the following information:
 * id_page, title_page, infobox_content. The three fields are separated by a tab character.
 */
public class ExtractInfoBoxes {

	public static final File ROOT_DIR = new File("graphipedia-data");
		
	/**
	 * The directory where the files of the Neo4j database are written.
	 */
	 public static final File NEO4J_DIR = new File(ROOT_DIR, "neo4j-db");
		
	/**
	 * Entry point of the program
	 * @param args Command-line arguments. (the XML file of the Wikipedia version to parse).
	 * @throws Exception when something goes wrong while parsing the Wikipedia articles. 
	 *  
	 */
	public static void main(String[] args) throws Exception {

		Logger logger = LoggerFactory.createLogger("ExtractInfoBoxes");
		if ( args.length != 2 ) {
			logger.severe("USAGE: java -jar ExtractInfoBoxes.jar xml-wikipedia-file lang-code");
			System.exit(-1);
		}

		File inputFile = new File(args[0]);
		String language = args[1];
		if ( !inputFile.exists() ) {
			logger.severe("File " + inputFile.getAbsolutePath() + " does not exist");
			System.exit(-1);
		}
		logger.info("Start extracting data...");
		long startTime = System.currentTimeMillis();
		Namespaces ns = null;
			try {
			logger.info("Reading the namespaces...");
			ns = extractNamespaces(inputFile);
		} catch (Exception e) {
			logger.severe("Error while reading the namespaces");
			e.printStackTrace();
			System.exit(-1);
		}
		
		HashMap<String, String> itRootCategories = new HashMap<String, String>();
		InputStream ibTemplatesFile = new FileInputStream("it-root-categories.csv");
		BufferedReader bd = new BufferedReader(new InputStreamReader(ibTemplatesFile));
		String line;
		while( (line = bd.readLine()) != null ) { 
			String[] values = line.split("\t");
			itRootCategories.put(values[0], values[1]);
		}
		bd.close();
		
		GraphipediaSettings settings = new GraphipediaSettings(ExtractInfoBoxes.ROOT_DIR, ExtractInfoBoxes.NEO4J_DIR);
		String itRootCategory = itRootCategories.get(language);
		
		InfoboxTemplatesExtractor itExtractor = 
				new InfoboxTemplatesExtractor(settings, language, itRootCategory, "");
		itExtractor.start();
		
		try {
			itExtractor.join();
		} catch (InterruptedException e) {
			logger.severe("Problems with the threads.");
			e.printStackTrace();
			System.exit(-1);
		}
		
		InfoboxTemplates ibTemplates = itExtractor.infoboxTemplates();
		
		try {
			logger.info("Extracting the infoboxes...");
			BufferedWriter bw = new BufferedWriter(new FileWriter("infoboxes_" + language + ".csv"));
			XmlInfoBoxFileParser extractor = new XmlInfoBoxFileParser(bw, ns, ibTemplates, logger);
			extractor.parse(inputFile.getAbsolutePath());
			bw.close();
		}
		catch(Exception e) {
			logger.severe("Error while extracting the introductions");
			e.printStackTrace();
			System.exit(-1);
		}
		long elapsed = System.currentTimeMillis() - startTime;
		logger.info("Introductions extracted in " + ReadableTime.readableTime(elapsed));
	}

	/**
	 * Extract the namespaces of the Wikipedia edition being parsed.
	 * @param inputFile The XML file of the Wikipedia version.
	 * @return The namespaces.
	 *  
	 * @throws Exception when something goes wrong while parsing the input file.
	 */
	private static Namespaces extractNamespaces(File inputFile) throws Exception {
		NamespaceExtractor nsExtractor = new NamespaceExtractor();
		nsExtractor.parse(inputFile.getAbsolutePath());
		return nsExtractor.namespaces();
	}
}

/**
 * Parser used to get the introduction of each page. 
 *
 */
class XmlInfoBoxFileParser extends SimpleStaxParser {

	/** 
	 * A counter used to track the progress of this extractor.
	 */
	private ProgressCounter pageCounter;

	/**
	 * The output file.
	 */
	private BufferedWriter bw;

	/**
	 * The title of the page that is being currently parsed from the input file.
	 */
	private String title;

	/**
	 * The text (the wiki code) of the page that is being currently parsed from the input file.
	 */
	private String text;

	/**
	 * The identifier of the page that is being currently parsed from the input file.
	 */
	private String id;

	/**
	 * The list of the namespaces in the Wikipedia edition being currently processed.
	 */
	private Namespaces ns;
	
	private InfoboxTemplates it;

	/**
	 * The parser of the introduction of a Wikipedia page.
	 */
	private IntroductionParser introParser;

	/**
	 * The parser of the infobox of a Wikipedia page.
	 */
	private InfoboxParser infoboxParser;

	/**
	 * The object used to clean the wiki code of a Wikipedia page.
	 */
	private WikiTextCleaner  cleaner;
	
	private WikiTextParser wikiTextParser;

	/**
	 * Creates the parser.
	 * @param bw The output file, where the introduction of each page is written.
	 * @param ns The namespaces of the Wikipedia edition being parsed.
	 * @param logger The logger of the program.
	 */
	public XmlInfoBoxFileParser(BufferedWriter bw, Namespaces ns, InfoboxTemplates it, Logger logger) {
		super(Arrays.asList(XmlFileTags.page.toString(), XmlFileTags.title.toString(), 
				XmlFileTags.text.toString(), XmlFileTags.id.toString()), 
				Arrays.asList(""));
		this.bw = bw;
		this.ns = ns;
		this.it = it;
		this.title = null;
		this.text = null;
		this.id = null;
		this.introParser = new IntroductionParser();
		this.infoboxParser = new InfoboxParser(it);
		DisambiguationPages dp = null;
		this.wikiTextParser = new WikiTextParser(this.ns, this.it, dp);
		
		this.pageCounter = new ProgressCounter(logger);
		try {
			this.cleaner = new WikiTextCleaner(ns);
		} catch (Exception e) {
			logger.severe("Error while loading the script to clean the wiki code of a Wikipedia page");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	protected boolean handleElement(String element, String value) throws XMLStreamException {
		if (XmlFileTags.page.toString().equals(element)) {
			Namespace pageNamespace = ns.wikipediaPageNamespace(title); 
			if (  pageNamespace.id() == Namespace.MAIN ) {
				
				Infobox infobox = (new InfoboxParser(this.it)).parse(value);
				if(infobox != null) {
					writePage(title, id, infobox.text());
				}
				
				//writePage(title, id, text); /// regular Wikipedia page.
			}
			title = null;
			text = null;
			id = null;
		} else if (XmlFileTags.title.toString().equals(element)) {
			title = value;
		} else if (XmlFileTags.text.toString().equals(element)) {
			text = value;
		} else if (XmlFileTags.id.toString().equals(element)) {
			if (id == null) // there are multiple ids that are specified in the input file, the first is the one associated with the page, the others with the revisions...
				id = value;
		}
		return true;
	}

	@Override
	protected boolean handleElement(String element, String value, List<String> attributeValues)
			throws XMLStreamException {
		return true;
	}

	/**
	 * Writes the introduction of a page to the output file. 
	 * @param title The title of the page.
	 * @param id The identifier of the page
	 * @param text The text of the page.
	 */
	private void writePage(String title, String id, String text) {
		Introduction intro = introParser.parse(text);
		if ( intro != null ) {
			String introText = intro.text();
			
			try {
				introText = cleaner.cleanText(title, Integer.parseInt(id), introText);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
			introText = introText.replaceAll("\n", " ");
			introText = introText.replaceAll("\t", " ");
			try {
				bw.write(id + "\t" + title + "\t" + introText + "\n");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		pageCounter.increment("Parsing pages");
	}
}