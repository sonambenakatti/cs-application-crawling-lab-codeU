package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
    //the URL where we start crawling
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// LinkedList to keep track of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to read and parse pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
        //inserts an element if possible; otherwise returns false
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        if(queueSize() == 0){ //if the queue is empty
            return null;
        }
        //choose and remove a URL from queue in FIFO order
        String url = queue.poll();
        System.out.println("Crawling " + url);

        //If the URL is already indexed, it should not index it again, and should return null
        if(testing == false && index.isIndexed(url)) {
            return null;
        } 
        Elements paragraphs; //must declare outside if/else statements to be in scope
        //Otherwise it should read the contents of the page using WikiFetcher.fetchWikipedia
		if (testing) {
			paragraphs = wf.readWikipedia(url);
		} else {
			paragraphs = wf.fetchWikipedia(url);
		}
        //index the page
        index.indexPage(url, paragraphs);
        //add links to the queue
        queueInternalLinks(paragraphs);
        //return the URL of the page it indexed.
        return url;        
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        for (Element para: paragraphs) {
            //select all elements with an href attribute
            for (Element e: para.select("a[href]")) {
                //get attributes and values of the selected elements
                String urlToAdd = e.attr("href");
                if (urlToAdd.startsWith("/wiki/")) {
                    String finalUrl = "https://en.wikipedia.org" + urlToAdd;
                    queue.offer(finalUrl);
                }
            }
        }
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);
            break;
            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
