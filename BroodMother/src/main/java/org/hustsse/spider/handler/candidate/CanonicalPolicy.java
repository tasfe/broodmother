package org.hustsse.spider.handler.candidate;

import org.hustsse.spider.framework.UrlUniqFilter;
import org.hustsse.spider.model.CrawlURL;

/**
 *
 * Canonicalize URL的策略，{@link UrlUniqFilter}使用{@link CrawlURL#getCanonicalStr()}
 * 作为判重的依据。
 *
 * @author Anderson
 *
 */
public interface CanonicalPolicy {

	/**
	 * return the canonicalized style of an CrawlURL
	 * @param url
	 * @return
	 */
	String getCanonicalStrFor(CrawlURL url);

}
