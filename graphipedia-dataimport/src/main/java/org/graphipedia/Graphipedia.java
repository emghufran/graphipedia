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
package org.graphipedia;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.graphipedia.dataextract.ExtractCrossLinks;
import org.graphipedia.dataextract.ExtractData;
import org.graphipedia.dataimport.ImportCrossLinks;
import org.graphipedia.dataimport.ImportGraph;
import org.graphipedia.download.WikipediaEdition;
import org.graphipedia.progress.CheckPoint;
import org.graphipedia.progress.LoggerFactory;
import org.graphipedia.progress.ReadableTime;
import org.graphipedia.wikipedia.Namespaces;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.unsafe.batchinsert.BatchInserter;


/**
 * Main class of Graphipedia.
 * Graphipedia automatically downloads the necessary dump files of selected Wikipedia editions and imports 
 * them to a Neo4j database.
 * The data is downloaded to directory {@code ROOT_DIR}, that is created by Graphipedia itself.
 * Note that if this directory already exists (from a previous computation) Graphipedia will flag an error and 
 * will stop.  
 */
public class Graphipedia {

	/**
	 * Name of the directory that Graphipedia creates in the working directory to put all the necessary files to import
	 * the Wikipedia editions.
	 */
	public static final File ROOT_DIR = new File("graphipedia-data");
	
	/**
	 * The directory where the files of the Neo4j database are written.
	 */
	public static final File NEO4J_DIR = new File(ROOT_DIR, "neo4j-db");
	
	/**
	 * The root categories of the disambiguation pages across all Wikipedia language editions.
	 */
	private Map<String, String> dpRootCategories;

	/**
	 * The root categories of the infobox templates across all Wikipedia language editions.
	 */
	private Map<String, String> itRootCategories;

	/**
	 * The logger of Graphipedia.
	 */
	private Logger logger;

	/**
	 * The checkpoint information that is maintained by Graphipedia so as some operations do not need
	 * to be performed from scratch in case a previous computation fails.
	 */
	private CheckPoint checkpoint;
	
	/**
	 * The settings of Graphipedia.
	 */
	private GraphipediaSettings settings;

	/**
	 * Creates a new instance of Graphipedia.
	 */
	public Graphipedia() {
		this.logger = LoggerFactory.createLogger("Graphipedia");
		this.checkpoint = new CheckPoint(ROOT_DIR);
		this.settings = new GraphipediaSettings(ROOT_DIR, NEO4J_DIR);
		this.dpRootCategories = new HashMap<String, String>();
		this.itRootCategories = new HashMap<String, String>(); 
		logger.info("Graphipedia started");
	}

	/**
	 * Entry point of the program.
	 * @param args Command-line arguments.
	 */
	public static void main(String[] args) {
		Graphipedia self = new Graphipedia();
		Set<String> languageEditions = self.getLanguageEditions(args);
		// Takes care of the creation of the root directory and 
		//the directory that is supposed to host the Neo4j database.
		self.rootDirectory();
		// Loads the resources, namely the root categories of the disambiguation pages and infobox templates across all 
		// Wikipedia language editions.
		self.loadResources(); // not sure if i need this?
		// Loads the checkpoint
		self.loadCheckpoint();
		
		try {
			// Starts the import.
			self.importEditions(languageEditions);
		} catch (Exception e) {
			self.logger.severe("Problem while importing the Wikipedia editions");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * Returns the set of the language editions to import, as specified in the command-line arguments of Graphipedia, if any. 
	 * @param args The command-line arguments of Graphipedia.
	 * @return The set of the codes of the languages of the Wikipedia editions to import, if specified in the command-line arguments.
	 * {@code NULL} otherwise.
	 */
	private Set<String> getLanguageEditions(String[] args) {
		if ( args.length > 1 ) {
			logger.severe("USAGE: java -jar Graphipedia.jar [langlist]\n"
					+ "langlist is the list of the codes of the languages of the editions to import, separated by a comma (e.g., en,fr,it),"
					+ "\n If no list is specified, Graphipedia will import all language editions available.");
			System.exit(-1);
		}
		Set<String> languageEditions = null;
		if ( args.length == 1 ) {
			languageEditions = new HashSet<String>();
			String[] langs = args[0].split(",");
			for ( String lang : langs ) 
				languageEditions.add(lang.toLowerCase());
			
		}
		return languageEditions;
	}

	/**
	 * Loads the resources, namely the disambiguation pages root categories and the infobox templates root categories.
	 */
	private void loadResources() {
		ClassLoader classLoader = getClass().getClassLoader();
		try {
			loadResource(classLoader.getResourceAsStream("dp-root-categories.csv"), this.dpRootCategories);
		} catch (IOException e) {
			logger.severe("Impossible to load the root categories of the disambiguation pages.");
			e.printStackTrace();
			System.exit(-1);
		}
		try {
			loadResource(classLoader.getResourceAsStream("it-root-categories.csv"), this.itRootCategories);
		} catch (IOException e) {
			logger.severe("Impossible to load the root categories of the infobox templates.");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * Loads a resource.
	 * @param file The file containing the resource.
	 * @param destination Wheere the destination is loaded.
	 * @throws IOException when something goes wrong while reading the resource.
	 */
	private void loadResource(InputStream file, Map<String, String> destination) throws IOException {
		System.out.println(file.toString());
		BufferedReader bd = new BufferedReader(new InputStreamReader(file));
		String line;
		while( (line = bd.readLine()) != null ) { 
			String[] values = line.split("\t");
			destination.put(values[0], values[1]);
		}
		bd.close();

	}
	
	/**
	 * Loads the checkpoint information from file. 
	 */
	private void loadCheckpoint() {
		try {
			this.checkpoint.load();

		} catch (IOException e) {
			logger.severe("Error while reading the checkpoint file " + this.checkpoint.getCheckPointFile().getAbsolutePath());
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Creates the root directory of Wikipedia, if it does not exist yet, and the
	 * Neo4j directory as a subdirectory of the root directory.
	 */
	private void rootDirectory() {
		if ( !ROOT_DIR.exists() ) {
			if ( !ROOT_DIR.mkdir() ) {
				logger.severe("Failed to create directory " + ROOT_DIR.getAbsolutePath());
				System.exit(-1);
			}
			if (!NEO4J_DIR.mkdir()) {
				logger.severe("Failed to create directory " + NEO4J_DIR.getAbsolutePath());
				System.exit(-1);
			}
		}
		else
		{
			try {
				FileUtils.deleteDirectory(NEO4J_DIR);
			} catch (IOException e) {
				logger.severe("Failed to delete directory " + NEO4J_DIR.getAbsolutePath());
				e.printStackTrace();
				System.exit(-1);
			}
			if (!NEO4J_DIR.mkdir()) {
				logger.severe("Failed to create directory " + NEO4J_DIR.getAbsolutePath());
				System.exit(-1);
			}
		}	
	}
	
	/**
	 * Import all Wikipedia language editions to the Neo4j database.
	 * @param languageEditions The set of the codes of the languages of the Wikipedia editions to import.
	 * @throws Exception when some error occurs.
	 */
	private void importEditions(Set<String> languageEditions) throws Exception {
		long startTime = System.currentTimeMillis();
		//Neo4jConnector neo4jConnector = new Neo4jConnector(settings, logger);
		//BatchInserter inserter = neo4jConnector.connectToNeo4jInserter();
		List<WikipediaEdition> wikipediaEditions = wikipediaEditions(languageEditions);
		int nbEditions = wikipediaEditions.size();
		int current = 0;
		ExtractData extractData = null;
		//ImportGraph graphImporter = null;
		Map<String, Namespaces> namespaces = new HashMap<String, Namespaces>();
		for ( WikipediaEdition edition : wikipediaEditions ) 
			settings.addLanguage(edition.languageCode());
		for ( WikipediaEdition edition : wikipediaEditions ) {
			current += 1;
			String language = edition.language();
			String languageCode = edition.languageCode();
			String languageLocal = edition.languageLocal();
			String suffix = languageCode.toUpperCase() + " " + current + "/" + nbEditions;
			long editionStartTime = System.currentTimeMillis();
			logger.info("Downloading the Wikipedia in " + language + " (" + languageLocal + ", " + languageCode.toUpperCase() + "), " + current + "/" + nbEditions);
			if ( !edition.download(checkpoint, logger, settings.wikipediaEditionDirectory(languageCode) ) )
				System.exit(-1);
			extractData = new ExtractData(settings, languageCode, this.dpRootCategories.get(languageCode), 
					this.itRootCategories.get(languageCode), checkpoint, suffix);
			extractData.start();
			extractData.join();
			namespaces.put(languageCode, extractData.getNamespaces());
			//if ( graphImporter != null )
			//	graphImporter.join(); // wait for the previous import to finish, if it's still running.
			//graphImporter = new ImportGraph(inserter, settings, languageCode, extractData.geotags(), 
			//		suffix, editionStartTime);
			//graphImporter.start();
			
			
		}
		/*
		if (graphImporter != null)
			graphImporter.join();
			
		neo4jConnector.disconnectFromNeo4jInserter(inserter);
		*/
		//importCrossLinks(neo4jConnector, namespaces);
		long elapsed = System.currentTimeMillis() - startTime;
		logger.info("Deleting the files downloaded from Wikimedia and the checkpoint file...");
		for ( WikipediaEdition edition : wikipediaEditions ) 
			FileUtils.deleteDirectory(settings.wikipediaEditionDirectory(edition.languageCode()));
		this.checkpoint.getCheckPointFile().delete();
		logger.info(nbEditions + " Wikipedia editions imported in " + ReadableTime.readableTime(elapsed));
	}
	
	/**
	 * Imports the cross-language links.
	 * @param neo4jConnector The object used to connect to a Neo4j database.
	 * @param namespaces The set of namespaces for each Wikipedia language edition that is imported.
	 */
	private void importCrossLinks(Neo4jConnector neo4jConnector, Map<String, Namespaces> namespaces) {
		try {
			logger.info("Import cross-language links");
			String[] languages = settings.languages();
			GraphDatabaseService graphDb = neo4jConnector.connectToNeo4jReader();
			for ( int i = 0; i < languages.length; i +=5 ) {
				int j = Math.min(5, languages.length - i);
				ExtractCrossLinks[] extractors = new ExtractCrossLinks[j];
				for ( int k = 0; k < extractors.length; k += 1 ) {
					String suffix = languages[i + k].toUpperCase() + " " + (i+k+1) + "/" + languages.length;
					extractors[k] = new ExtractCrossLinks(graphDb, settings, languages, namespaces, languages[i + k], checkpoint, suffix); 
					extractors[k].start();
				}
				for ( int k = 0; k < extractors.length; k += 1 ) 
					extractors[k].join();
			}
			neo4jConnector.disconnectFromNeo4jReader(graphDb);
			BatchInserter inserter = neo4jConnector.connectToNeo4jInserterNoIndexes();
			for ( int i = 0; i < languages.length; i +=1 ) {
				String suffix = languages[i].toUpperCase() + " " + (i+1) + "/" + languages.length;
				ImportCrossLinks importer = new ImportCrossLinks(settings, inserter, languages[i], suffix);
				importer.start();
				importer.join();
			}
			neo4jConnector.disconnectFromNeo4jInserter(inserter);
		}
		catch(InterruptedException e) {
			logger.severe("Something wrong with the threads. Should not happen");
			e.printStackTrace();
			System.exit(-1);
		}
	}
	

	/**
	 * Loads the list of Wikipedia editions from file.
	 * @param languageEditions The set of the codes of the languages of the Wikipedia editions to import.
	 * @return The list of Wikipedia editions to import.
	 */
	private List<WikipediaEdition> wikipediaEditions(Set<String> languageEditions) {
		List<WikipediaEdition> wikipediaEditions = new ArrayList<WikipediaEdition>();
		ClassLoader classLoader = getClass().getClassLoader();
		try {
			BufferedReader bd = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream("wikipedias.csv")));
			String line;
			while( (line = bd.readLine()) != null ) {
				String[] values = line.split("\t");
				String language = values[0];
				String languageLocal = values[1];
				String languageCode = values[2];
				if ( languageEditions == null || languageEditions.contains(languageCode) )
					wikipediaEditions.add(new WikipediaEdition(language, languageLocal, languageCode));
			}
			bd.close();
		}
		catch(IOException e) {
			logger.severe("Error while reading the file listing all the Wikipedia editions to import");
			e.printStackTrace();
			System.exit(-1);
		}
		return wikipediaEditions;
	}
	
}

