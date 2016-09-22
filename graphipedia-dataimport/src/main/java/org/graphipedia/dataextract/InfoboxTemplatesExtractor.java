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
package org.graphipedia.dataextract;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import org.graphipedia.GraphipediaSettings;
import org.graphipedia.progress.CheckPoint;
import org.graphipedia.progress.LoggerFactory;
import org.graphipedia.wikipedia.InfoboxTemplates;

/**
 * This thread extracts the infobox templates of a given Wikipedia language edition.
 *
 */
public class InfoboxTemplatesExtractor extends Thread {

	/**
	 * The logger of this class.
	 */
	private Logger logger;

	/**
	 * The list of all infobox templates extracted. 
	 */
	private InfoboxTemplates infoboxTemplates;


	/**
	 * The code of the language of the Wikipedia edition in which the 
	 * infobox templates are searched.
	 */
	private String language;

	/**
	 * The name of the category that contains all the infobox templates.
	 */
	private String rootCategory;

	/**
	 * The checkpoint information of Graphipedia.
	 */
	private CheckPoint checkpoint;

	public InfoboxTemplatesExtractor(GraphipediaSettings settings, String language, 
			String rootCategory, String loggerMessageSuffix) {
		this.infoboxTemplates = new InfoboxTemplates(settings.wikipediaEditionDirectory(language));
		this.language = language;
		this.rootCategory = rootCategory;
		this.logger = LoggerFactory.createLogger("Infobox Extractor (" + loggerMessageSuffix + ")");
		this.checkpoint = null;
	}
	/**
	 * Constructor.
	 * @param settings The settings of the import.
	 * @param language The code of the language of the Wikipedia edition in which the
	 * infobox templates are searched.
	 * @param rootCategory The name of the category that contains all the infobox templates.
	 * @param checkpoint The checkpoint information of Graphipedia.
	 * @param loggerMessageSuffix A suffix appended to all the messages of the logger.
	 */
	public InfoboxTemplatesExtractor(GraphipediaSettings settings, String language, 
			String rootCategory, CheckPoint checkpoint, String loggerMessageSuffix) {
		this.infoboxTemplates = new InfoboxTemplates(settings.wikipediaEditionDirectory(language));
		this.language = language;
		this.rootCategory = rootCategory;
		this.logger = LoggerFactory.createLogger("Infobox Extractor (" + loggerMessageSuffix + ")");
		this.checkpoint = checkpoint;
	}

	@Override
	public void run() {
		logger.info("Extracting infobox templates...");
		if ( this.rootCategory == null) {
			logger.warning("No root category for infobox templates. Skipping...");
			return;
		}
		File itFile = this.infoboxTemplates.getInfoboxTemplatesFile();
		if (checkpoint.isInfoboxExtracted(this.language)) {
			try {
				this.infoboxTemplates.load(itFile);
			} catch (IOException e) {
				logger.severe("Error while reading file " + itFile.getAbsolutePath());
				e.printStackTrace();
				System.exit(-1);
			}
		}
		else {
			try {
				this.infoboxTemplates.load(language, rootCategory, itFile, logger);
			} catch (Exception e) {
				logger.severe("Error while reading the infobox templates from MediaWiki");
				e.printStackTrace();
				System.exit(-1);
			}
			try {
				checkpoint.addInfoboxExtracted(this.language, true);
			} catch (IOException e) {
				logger.severe("Error while saving the checkpoint to file");
				e.printStackTrace();
				System.exit(-1);
			}
		}
		logger.info("Done");
	}

	/**
	 * Returns the list of the infobox templates extracted.
	 * @return The list of the infobox templates extracted.
	 */
	public InfoboxTemplates infoboxTemplates() {
		return this.infoboxTemplates;
	}

}
