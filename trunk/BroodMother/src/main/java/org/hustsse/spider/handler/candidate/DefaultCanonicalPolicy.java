package org.hustsse.spider.handler.candidate;

import javax.print.URIException;

import org.hustsse.spider.model.CrawlURL;
import org.hustsse.spider.model.URL;

/**
 * 默认标准化策略，特性如下：
 *
 * <pre>
 * 1) discard userinfo
 * 2) strip "www"
 * 3) https --> http //暂时不支持https协议
 * 4) normalize path, ie remove dot-segments int the path
 * 5) remove unnessary "?" when there is no query
 * </pre>
 *
 * 全部使用URL的decode形式。
 *
 * @author Anderson
 *
 */
public class DefaultCanonicalPolicy implements CanonicalPolicy {

	@Override
	public String getCanonicalStrFor(CrawlURL url) {
		URL burl = url.getURL();
		String protocal = burl.getProtocol().toLowerCase();
		// discard userinfo
		String host = burl.getDecodedHost().toLowerCase();
		// Strip 'www*'
		if (host.startsWith("www")) {
			host = host.replaceFirst("www[^\\.]*\\.", "");
		}
		int port = burl.getPort();

		// Limiting protocols
		if ("https".equals(protocal)) {
			protocal = "http";
			port = 80;
		}
		String path = burl.getDecodedPath().toLowerCase();

		// Removing dot-segments in the path
		String normalizedPath = new String(normalize(path.toCharArray()));

		String query = burl.getDecodedQuery();
		// query存在，才在canonical形式最后加上"?query"。
		if (query != null) {
			query = "?" + query.toLowerCase();
		} else {
			query = "";
		}

		String canon = protocal + "://" + host + ":" + port + normalizedPath + query;
		return canon;
	}

	/**
	 * normalize一个url的path <borrowed from org.apache.commons.httpclient.URI>
	 *
	 * Normalize the given hier path part.
	 *
	 * <p>
	 * Algorithm taken from URI reference parser at
	 * http://www.apache.org/~fielding/uri/rev-2002/issues.html.
	 *
	 * @param path
	 *            the path to normalize
	 * @return the normalized path
	 * @throws URIException
	 *             no more higher path level to be normalized
	 */
	protected char[] normalize(char[] path) {

		if (path == null) {
			return null;
		}

		String normalized = new String(path);

		// If the buffer begins with "./" or "../", the "." or ".." is removed.
		if (normalized.startsWith("./")) {
			normalized = normalized.substring(1);
		} else if (normalized.startsWith("../")) {
			normalized = normalized.substring(2);
		} else if (normalized.startsWith("..")) {
			normalized = normalized.substring(2);
		}

		// All occurrences of "/./" in the buffer are replaced with "/"
		int index = -1;
		while ((index = normalized.indexOf("/./")) != -1) {
			normalized = normalized.substring(0, index) + normalized.substring(index + 2);
		}

		// If the buffer ends with "/.", the "." is removed.
		if (normalized.endsWith("/.")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}

		int startIndex = 0;

		// All occurrences of "/<segment>/../" in the buffer, where ".."
		// and <segment> are complete path segments, are iteratively replaced
		// with "/" in order from left to right until no matching pattern
		// remains.
		// If the buffer ends with "/<segment>/..", that is also replaced
		// with "/". Note that <segment> may be empty.
		while ((index = normalized.indexOf("/../", startIndex)) != -1) {
			int slashIndex = normalized.lastIndexOf('/', index - 1);
			if (slashIndex >= 0) {
				normalized = normalized.substring(0, slashIndex) + normalized.substring(index + 3);
			} else {
				startIndex = index + 3;
			}
		}
		if (normalized.endsWith("/..")) {
			int slashIndex = normalized.lastIndexOf('/', normalized.length() - 4);
			if (slashIndex >= 0) {
				normalized = normalized.substring(0, slashIndex + 1);
			}
		}

		// All prefixes of "<segment>/../" in the buffer, where ".."
		// and <segment> are complete path segments, are iteratively replaced
		// with "/" in order from left to right until no matching pattern
		// remains.
		// If the buffer ends with "<segment>/..", that is also replaced
		// with "/". Note that <segment> may be empty.
		while ((index = normalized.indexOf("/../")) != -1) {
			int slashIndex = normalized.lastIndexOf('/', index - 1);
			if (slashIndex >= 0) {
				break;
			} else {
				normalized = normalized.substring(index + 3);
			}
		}
		if (normalized.endsWith("/..")) {
			int slashIndex = normalized.lastIndexOf('/', normalized.length() - 4);
			if (slashIndex < 0) {
				normalized = "/";
			}
		}

		return normalized.toCharArray();
	}

}
