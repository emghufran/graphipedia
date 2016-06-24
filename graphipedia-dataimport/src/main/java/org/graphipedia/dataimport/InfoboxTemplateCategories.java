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
package org.graphipedia.dataimport;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.wikipedia.Wiki;

/**
 * This auxiliary class obtains from the MediaWiki API the list of root categories (across Wikipedia language editions)
 * that include all
 * the templates that are used to generete infoboxes.
 * 
 * This class generates a CSV file, where in each line there is the code of the language of a Wikipedia edition and 
 * the name of the root category of the infobox templates. The two values are separated by a tab character.
 * 
 *  As with the disambiguation root categories, the infobox template root categories are unlikely to change
 *  and therefore this class should not be executed again (but we never know).
 *  The file generated by this class is used as a resource of this project. 
 *
 */
public class InfoboxTemplateCategories {

	/**
	 * Entry point of the program.
	 * @param args Command-line arguments (none).
	 * @throws IOException with I/O errors.
	 */
	public static void main(String[] args) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter("it-root-categories.csv"));
		Wiki wiki = new Wiki("en.wikipedia.org");
		Map<String, String> clLinks = wiki.getInterWikiLinks("Category:Infobox templates");
		for ( Entry<String, String> link : clLinks.entrySet() )
			bw.write(link.getKey() + "\t" + link.getValue() + "\n");
		bw.close();
	}

}