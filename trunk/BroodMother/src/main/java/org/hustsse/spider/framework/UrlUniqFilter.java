package org.hustsse.spider.framework;

/**
 * URL判重器
 * @author Administrator
 *
 */
public interface UrlUniqFilter {

	/**
	 * 添加一个url到“already seen”集合
	 *
	 * @param canonicalUrl url的canonical形式
	 * @return url已经存在，返回true；否则添加并返回false
	 */
	public boolean add(String canonicalUrl);

	public long count();

	/**
	 * 将一个url从“already seen”集合删除
	 * @param canonicalUrl
	 * @return 删除成功，返回true；url不在内部“already seen”中，返回false
	 */
	public boolean delete(String canonicalUrl);

}
