//
// Copyright (c) 2012 Mirko Nasato
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
package org.graphipedia.dataextract;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.codehaus.stax2.XMLOutputFactory2;
import org.graphipedia.GraphipediaSettings;
import org.graphipedia.progress.CheckPoint;
import org.graphipedia.progress.LoggerFactory;
import org.graphipedia.progress.ReadableTime;
import org.graphipedia.wikipedia.Geotags;
import org.graphipedia.wikipedia.Namespaces;

/**
 * This thread extracts the data of a specific Wikipedia language edition from the input files 
 * so as they can be imported later to the Neo4j database. 
 * The thread obtains the disambiguation pages, the infobox templates, the namespaces, the pages and the links 
 * among pages. 
 * This thread will create an intermediary file that will contain the data on the extracted Wikipdia pages and links.
 */
public class ExtractData extends Thread {

	/**
	 * The name of the temporary file that is created by this thread and used as input to the thread that imports the
	 * Wikipedia graph to Neo4j.
	 */
	public static final String TEMPORARY_LINK_FILE = "temporary-link-file.xml.bz2";

	/**
	 * The logger of this class.
	 */
	private Logger logger; 

	/**
	 * The settings of the program.
	 */
	private GraphipediaSettings settings;

	/**
	 * The code of the language of the Wikipedia edition being currently imported (e.g., en).
	 */
	private String language;

	/**
	 * The root category of the disambiguation pages.
	 */
	private String dpRootCategory;
	
	/**
	 * The root category of the infobox templates.
	 */
	private String itRootCategory;

	/**
	 * The namespaces.
	 */
	private Namespaces ns;
	
	/**
	 * The geotags associated to pages that describe spatial entities.
	 */
	private Map<String, Geotags> geotags;
	
	/**
	 * The checkpoint information of Graphipedia.
	 */
	private CheckPoint checkpoint;
	
	/**
	 * A suffix that is appended to the logger messages.
	 */
	private String loggerMessageSuffix;

	/**
	 * Creates a new thread to extract a specific Wikipedia language edition.
	 * @param settings The general settings of Graphipedia.
	 * @param language The code of the language of the Wikipedia edition being imported. 
	 * @param dpRootCategory The root category of the disambiguation pages. 
	 * @param itRootCategory The root category of the infobox templates.
	 * @param checkpoint The checkpoint information of Graphipedia.
	 * @param loggerMessageSuffix A suffix to append to the message displayed by the logger.
	 */
	public ExtractData(GraphipediaSettings settings, String language, String dpRootCategory, 
			String itRootCategory, CheckPoint checkpoint, String loggerMessageSuffix) {
		this.settings = settings;
		this.language = language;
		this.loggerMessageSuffix = loggerMessageSuffix;
		this.logger = LoggerFactory.createLogger("Extract data (" + loggerMessageSuffix + ")");
		this.dpRootCategory = dpRootCategory;
		this.itRootCategory = itRootCategory;
		this.checkpoint = checkpoint;
		this.geotags = new HashMap<String, Geotags>();
		
	}
	
	/**
	 * Returns the namespaces of the Wikipedia edition which this thread is extracting the 
	 * data from.
	 * @return The namespaces of the Wikipedia edition which this thread is extracting the 
	 * data from.
	 */
	public Namespaces getNamespaces() {
		return this.ns;
	}
	
	/**
	 * Returns the geotags associated to the pages that describe spatial entities.
	 * @return The geotags associated to the pages that describe spatial entities.
	 */
	public Map<String, Geotags> geotags() {
		return this.geotags;
	}

	@Override
	public void run() {
		logger.info("Start extracting data...");
		long startTime = System.currentTimeMillis();
		
		DisambiguationPageExtractor dpExtractor = 
				new DisambiguationPageExtractor(settings, this.language, dpRootCategory, checkpoint, loggerMessageSuffix);
		dpExtractor.start();
		ExtractNamespaces nsExtractor = new ExtractNamespaces(settings, language, 
				loggerMessageSuffix, new File(settings.wikipediaEditionDirectory(language), ExtractNamespaces.NAMESPACE_FILE), checkpoint);
		nsExtractor.start();
		InfoboxTemplatesExtractor itExtractor = 
				new InfoboxTemplatesExtractor(settings, language, itRootCategory, checkpoint, loggerMessageSuffix);
		itExtractor.start();
		//ExtractGeoTags geotagsExtractor = new ExtractGeoTags(settings, language, loggerMessageSuffix);
		//geotagsExtractor.start();
		try {
			dpExtractor.join();
			nsExtractor.join();
			this.ns = nsExtractor.namespaces();
			itExtractor.join();
			//geotagsExtractor.join();
			//this.geotags = geotagsExtractor.getGeoTags();
		} catch (InterruptedException e) {
			logger.severe("Problems with the threads.");
			e.printStackTrace();
			System.exit(-1);
		}
		XMLOutputFactory outputFactory = XMLOutputFactory2.newInstance();
		File outputFile = new File(settings.wikipediaEditionDirectory(language), TEMPORARY_LINK_FILE); 
		if (checkpoint.isLinksExtracted(this.language)) {
			logger.info("Using pages and links from a previous computation");
			return;
		}
		try {
			FileOutputStream fout = new FileOutputStream(outputFile.getAbsolutePath());
			BufferedOutputStream bos = new BufferedOutputStream(fout);
			CompressorOutputStream output = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2, bos);
			XMLStreamWriter writer = outputFactory.createXMLStreamWriter(output, "UTF-8");
			writer.writeStartDocument();
			writer.writeStartElement("d");

			LinkExtractor linkExtractor = new LinkExtractor(writer, logger, settings, language, 
					dpExtractor.disambiguationPages(), itExtractor.infoboxTemplates(), this.ns);
			linkExtractor.parse(settings.getWikipediaXmlFile(language).getAbsolutePath());
			writer.writeEndElement();
			writer.writeEndDocument();
			output.close();
			fout.close();
			bos.close();
			writer.close();
			long elapsed = System.currentTimeMillis() - startTime;
			logger.info("Data extracted in " + ReadableTime.readableTime(elapsed));
		}
		catch(Exception e) {
			logger.severe("Error while parsing the XML file ");
			e.printStackTrace();
			System.exit(-1);
		}
		try {
			checkpoint.addLinksExtracted(this.language, true);
		} catch (IOException e) {
			logger.severe("Error while saving the checkpoint to file");
			e.printStackTrace();
			System.exit(-1);
		}
		settings.getWikipediaXmlFile(language).delete();
	}
}
