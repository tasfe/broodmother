package org.hustsse.spider.framework;

/**
 * URL判重器
 * @author Administrator
 *
 */
public interface UrlUniqFilter {

	/**
	 * 添加一个url为“already seen”状态
	 *
	 * @param canonicalUrl url的canonical形式
	 * @return url已经存在，返回true；否则添加并返回false
	 */
	public boolean add(String canonicalUrl);

}
